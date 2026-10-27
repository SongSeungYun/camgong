# Data Model: 회원 가입·로그인·로그아웃

> 초안: 추천안 기준([research.md](research.md), 2026-10-26 확정).

## 변경 요약

| 대상 | 변경 |
|------|------|
| `member` | 컬럼 확장: `email`, `email_key`, `password_hash`, `display_name_key`, `consent_at`, `consent_version`, `created_at` |
| `pending_signup` | 새 테이블 (인증 전 가입 정보와 메일 한도) |
| `login_session` | 새 테이블 (서버 세션) |
| `login_attempt` | 새 테이블 (이메일 키 로그인 실패 횟수와 잠금) |

`schema.sql`은 앱 시작마다 재실행되고 MySQL은 `CREATE INDEX IF NOT EXISTS`를 지원하지 않는 것으로 알고 있으므로(구현 시 확인), 모든 인덱스와 유일 제약은 `CREATE TABLE IF NOT EXISTS` 정의 안에 넣는다. 이미 만들어진 `member` 테이블은 운영 데이터가 생기기 전(5단계 전)에는 정의를 직접 고치고, 그 뒤에는 Flyway로 올려야 한다.

## member (확장)

001의 최소 형태(`id`, `name`)를 다음으로 확장한다. 이름 컬럼은 spec 용어 `표시 이름`에 맞춰 `display_name`으로 바꾸는 것을 검토한다(코드 `Member.name`과의 변경 범위는 구현 때 정한다).

| 컬럼 | 타입 | 규칙 | 근거 |
|------|------|------|------|
| `id` | `BIGINT AUTO_INCREMENT` | PK | |
| `name` (표시 이름) | `VARCHAR(20)` | 필수, 2~20자, 공백만 불가 | FR-003 |
| `display_name_key` | `VARCHAR(20)` | 필수, `UNIQUE`. 이름을 소문자·앞뒤 공백 제거로 정규화한 값 | FR-003 |
| `email` | `VARCHAR(254)` | 필수, 인증을 마친 이메일 원문 | FR-002 |
| `email_key` | `VARCHAR(254)` | 필수, `UNIQUE`. 소문자·trim | FR-002 |
| `password_hash` | `VARCHAR(100)` | 필수. BCrypt 해시. 원문은 저장하지 않는다 | FR-002, SC-009 |
| `consent_at` | `DATETIME(6)` | 필수. 개인정보 수집·이용 동의 시각 | FR-017 |
| `consent_version` | `VARCHAR(20)` | 필수. 동의한 안내문 버전 | FR-017 |
| `created_at` | `DATETIME(6)` | 필수. 가입 완료(인증) 시각 | |

표시 이름·이메일의 유일성은 DB 기본 collation(`utf8mb4_0900_ai_ci`)을 따르므로 대소문자뿐 아니라 악센트도 무시한다(`José`와 `Jose`는 같은 이름). 2026-10-28 검수에서 확인했고 그대로 두기로 했다.

`member`에는 인증을 마친 회원만 있다. 001~007의 테스트가 이름만으로 회원을 시드하던 방식은 `IntegrationTestBase`가 `email`·`password_hash`·`consent_*`를 채우도록 바꿔야 한다(교차 영향).

## pending_signup (인증 전 가입 신청)

| 컬럼 | 타입 | 규칙 | 근거 |
|------|------|------|------|
| `id` | `BIGINT AUTO_INCREMENT` | PK | |
| `email_key` | `VARCHAR(254)` | 필수, `UNIQUE` (이메일당 신청 하나) | FR-006 |
| `email` | `VARCHAR(254)` | 필수 | |
| `display_name` | `VARCHAR(20)` | 필수. 점유하지 않는다(유일 제약 없음) | FR-003 |
| `password_hash` | `VARCHAR(100)` | 필수 | |
| `consent_at` / `consent_version` | `DATETIME(6)` / `VARCHAR(20)` | 필수 | FR-017 |
| `token_hash` | `CHAR(64)` | 필수. 인증 토큰의 SHA-256 | FR-004 |
| `token_expires_at` | `DATETIME(6)` | 필수. 발송 시각 + 24시간 | FR-004 |
| `last_sent_at` | `DATETIME(6)` | 필수. 마지막 발송 시각 | FR-005 |
| `window_started_at` | `DATETIME(6)` | 필수. 24시간 창의 시작 | FR-005 |
| `sent_count` | `INT` | 필수. 현재 창의 발송 횟수 (최대 5) | FR-005 |
| `used_at` | `DATETIME(6)` | NULL. 인증에 성공한 시각. 행을 지우지 않고 채워서 `TOKEN_USED`와 `INVALID_TOKEN`을 구분한다 | FR-004 |
| `created_at` | `DATETIME(6)` | 필수 | |

## login_session

| 컬럼 | 타입 | 규칙 | 근거 |
|------|------|------|------|
| `id` | `BIGINT AUTO_INCREMENT` | PK | |
| `token_hash` | `CHAR(64)` | 필수, `UNIQUE`. 세션 id의 SHA-256 | 결정 9 |
| `member_id` | `BIGINT` | 필수. `KEY (member_id)` | FR-011 |
| `created_at` | `DATETIME(6)` | 필수 | |
| `last_used_at` | `DATETIME(6)` | 필수. 슬라이딩 기준(10분에 한 번 갱신) | FR-007 |

만료는 `last_used_at + 14일`. 폐기(로그아웃)는 행 삭제다. `KEY (last_used_at)`으로 정리 작업이 만료 행을 찾는다.

## login_attempt

| 컬럼 | 타입 | 규칙 | 근거 |
|------|------|------|------|
| `email_key` | `VARCHAR(254)` | PK. 존재하지 않는 이메일도 같은 방식으로 센다 | FR-010·011 |
| `failed_count` | `INT` | 필수. 연속 실패 횟수 | FR-011 |
| `locked_until` | `DATETIME(6)` | NULL. 잠금 해제 시각 | FR-011 |
| `updated_at` | `DATETIME(6)` | 필수. 정리 기준 | |

## 불변식

| # | 불변식 | 지키는 곳 |
|---|--------|-----------|
| (002) | 이메일과 표시 이름은 인증을 마친 회원 사이에서만 유일하다 | `member`의 `UNIQUE (email_key)`, `UNIQUE (display_name_key)` |
| (002) | 인증 링크는 24시간, 한 번만 유효하며 재발송하면 이전 것은 무효 | `pending_signup.token_hash` 교체, 사용 시 `used_at` 기록(행은 정리 작업이 지운다) |
| (002) | 인증 메일은 같은 이메일에 1분 간격, 24시간 5회 이하 | `pending_signup`의 조건부 `UPDATE` |
| (002) | 비밀번호·토큰·세션 id는 원문으로 저장되지 않는다 | 해시 컬럼만 둔다 |
| (002) | 연속 5회 실패하면 15분간 잠긴다 (없는 계정도 동일) | `login_attempt`의 조건부 `UPDATE` |
| (002) | 로그아웃한 세션은 즉시 쓸 수 없다 | 세션 행 삭제 |
| (002) | 다른 회원에게는 표시 이름만 보인다 | 응답 DTO에 `email`을 두지 않는다 |

## 쓰는 쿼리 (요지)

메일 한도 확인·증가 (한 문장으로 원자적):

```sql
UPDATE pending_signup SET
  sent_count = IF(window_started_at < :now - INTERVAL 24 HOUR, 1, sent_count + 1),
  window_started_at = IF(window_started_at < :now - INTERVAL 24 HOUR, :now, window_started_at),
  last_sent_at = :now, token_hash = :hash, token_expires_at = :now + INTERVAL 24 HOUR
WHERE email_key = :k AND last_sent_at <= :now - INTERVAL 1 MINUTE
  AND (window_started_at < :now - INTERVAL 24 HOUR OR sent_count < 5)
```

영향 행 0이면 한도에 걸린 것이다(`useAffectedRows=true` 전제, 이유를 구분하려면 `SELECT`로 진단). `SET` 절은 왼쪽에서 오른쪽으로 평가되는 MySQL 규칙(spikes #10)에 기댄 순서다 — 구현 때 검증한다.

로그인 실패 기록:

```sql
INSERT INTO login_attempt (email_key, failed_count, locked_until, updated_at) VALUES (:k, 1, NULL, :now)
ON DUPLICATE KEY UPDATE
  failed_count = IF(locked_until IS NOT NULL AND locked_until <= :now, 1, failed_count + 1),
  locked_until = IF(failed_count >= 5,
                    IF(locked_until IS NULL OR locked_until <= :now, :now + INTERVAL 15 MINUTE, locked_until),
                    IF(locked_until IS NOT NULL AND locked_until <= :now, NULL, locked_until)),
  updated_at = :now
```

위 수식은 구현(T034)이 바로잡은 것이다. 처음 문서의 `IF(failed_count >= 5, now+15분, locked_until)`은 잠금이 풀린 뒤에도 이전 `locked_until`이 남아 다음 실패마다 횟수가 1로 돌아가 영영 5에 닿지 못했다. 이미 잠긴 상태에서 늦게 도착한 실패가 잠금을 연장하지 않는 것도 이 식이 보장한다. `SET` 평가 순서(왼쪽→오른쪽)는 MySQL 8.4에서 확인됐다.

(잠금 중에는 로그인 자체를 시도하지 않고 거부한다. `ON DUPLICATE KEY UPDATE`의 별칭 문법과 평가 순서는 구현 때 검증한다.)

세션 검증은 `token_hash`로 조회해 `last_used_at + 14일 > now`를 확인하고, `last_used_at < now - 10분`일 때만 갱신한다.
