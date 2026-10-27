# 아키텍처 (초안)

**작성**: 2026-10-26 | **상태**: 확정(2026-10-26), 변경은 design-plan 결정 기록에 남긴다 | **관련**: [design-plan.md](design-plan.md), [domain.md](domain.md)(작성 예정), [screens.md](screens.md)

각 항목의 **상태**: 확정(헌법·사용자 결정), 기본값(내가 정함, 바꿔도 됨), 미정(해당 단계에서 결정).

## 1. 한 줄 요약

기숙사생이 공동구매를 열고 수량 단위로 나눠 참여하며, 수량이 차면 모집이 끝난다. 백엔드 REST API 하나(Spring Boot + MySQL)로 시작하고 화면은 4단계에서 붙인다.

## 2. 기능 지도

| 번호 | 기능 | 단계 | 의존 | 문서 |
|------|------|------|------|------|
| 001 | 공구 개설·수정·취소 | 2 | 회원 | spec, plan, tasks 완료 |
| 002 | 로그인·가입 | 4 | | |
| 003 | 공구 목록·상세 조회(검색·카테고리 포함) | 2 | 001 | N+1 소재 |
| 004 | 참여·참여 취소·수량 변경 | 3 | 001 | 마지막 수량 경쟁, 4방식 비교 |
| 005 | 마감 확정(성공·실패)+스케줄러 | 3 | 001, 004 | 중복 실행 방지 |
| 006 | 개설자 체크(참여자·입금·수령) | 4 이후 | 004 | 명단은 개설자에게만 |
| 007 | 알림 | 4 이후 | 001, 004, 005 | 전달 수단 미정 |

구현 순서는 001 → 003 → 004 → 005다(단계 2, 3). 001만 먼저 만들면 데이터를 SQL로 시드해야 하므로, 003과 004를 이어서 만들어야 end-to-end로 볼 수 있다.

## 3. 기술 스택

| 영역 | 선택 | 상태 |
|------|------|------|
| 언어, 프레임워크 | Java 21, Spring Boot (프로젝트 생성 시점 최신 안정판), Spring Web, Data JPA, Validation | 확정(헌법) |
| DB | MySQL 8.4 (로컬·테스트에서 같은 태그 고정) | 확정(헌법) |
| 빌드 | Gradle | 기본값 |
| 테스트 | JUnit 5, Spring Boot Test, Testcontainers(MySQL) | 확정(헌법 IV) |
| 부하 측정 | k6 | 확정(헌법 II) |
| 스키마 관리 | `schema.sql` + `ddl-auto=validate`. Flyway로 올리는 시점은 운영 데이터가 생기는 5단계 전 | 기본값 |
| 인증 | 이메일+비밀번호, 학교 이메일 인증(002 spec 확정). 세션/토큰 방식과 메일 발송 수단은 002 plan에서. 그전까지 임시 `X-Member-Id` | 부분 확정 |
| 프론트엔드 | 모바일 우선 웹 + PWA(홈 화면에 추가). 네이티브 앱은 만들지 않는다. 스택은 008 plan에서 | 확정(2026-10-27, 방식) / 스택 미정 |
| 배포 | Docker, 대상 미정 | 미정(4단계) |

## 4. 저장소 구조

```text
camgong/
├── build.gradle, settings.gradle      # 루트가 Gradle 프로젝트 (구현 시작 시 생성)
├── src/main/java/com/camgong/         # 백엔드
├── src/main/resources/
├── src/test/java/com/camgong/
├── docs/                              # 프로젝트 단위 문서
│   ├── design-plan.md, architecture.md, domain.md, screens.md, spikes.md
├── specs/NNN-feature/                 # Spec Kit 기능별 문서
├── .specify/, .claude/                # Spec Kit 설정
└── frontend/                          # 4단계에서 필요하면 옆에 추가
```

프론트가 같은 저장소에 들어가도 루트 Gradle 프로젝트와 충돌하지 않는다. 따로 저장소로 분리할지는 4단계에서 정한다.

## 5. 패키지 구조 (백엔드)

기능별 패키지로 나눈다.

```text
com.camgong
├── common/       # 오류 처리, 공통 예외
├── member/
├── groupbuy/     # 애그리거트(GroupBuy, Participation, 리포지토리, 상태, 이벤트)와 001 쓰기
│   ├── query/    # 003 조회 (읽기 전용)
│   ├── join/     # 004 참여·수량 변경·참여 취소 (QuantityGate 4방식)
│   └── close/    # 005 마감 확정 (스케줄러)
└── (이후) auth/, notification/
```

레이어는 `Controller → Service → Repository`이고, 서비스·리포지토리의 인터페이스는 구현체가 하나뿐이면 만들지 않는다.
엔티티는 연관관계 매핑 없이 `Long` id 컬럼만 두고, N+1 비교용 매핑은 003에서 정한다.

**결정 (2026-10-26)**: `Participation`은 `groupbuy` 패키지 안에 둔다. 클래스가 약 40개가 되므로 기능별 하위 패키지 `query`(003), `join`(004), `close`(005)로 나누고, 애그리거트와 001 쓰기는 `groupbuy` 루트에 둔다. 하위 패키지가 애그리거트를 쓰므로 `GroupBuy`·`Participation`의 접근자와 상태 전이 메서드는 `public`이어야 한다. 참여는 공구의 일부여서 `secured_quantity = Σ quantity` 불변식과 락이 공구 행 하나에 걸려 있다. 합치면 패키지 순환이 없고 불변식을 한곳에서 지킬 수 있다. 001 plan과 tasks의 경로를 맞췄다.

## 6. 공통 규약

### 6.1 API

- 경로는 `/api/...`, 버전 접두사는 두지 않는다(필요해질 때 추가). JSON 필드는 camelCase, DB 컬럼은 snake_case. 상태: 기본값
- 상태 코드: 400 입력 오류, 401 로그인 필요, 403 권한 없음, 404 없음, 409 상태 충돌. 상태: 기본값
- 응답의 상태 필드는 `status` 하나이고 값은 `OPEN`, `PENDING_CLOSE`, `COMPLETED`, `FAILED`(표시 상태)다. 취소된 공구는 어떤 API에서도 404다. 상태: 확정(교차 점검)
- 오류 본문은 RFC 7807 `ProblemDetail`에 `errors: [{field, message}]`를 더한 형식이다. 상태: 확정(001 plan)
- 목록은 페이지네이션 방식(오프셋/커서)을 003 plan에서 정한다. 상태: 확정(003 plan, 키셋)
- **오류 코드 표** (오류 본문 `title`에 코드를 쓴다). 상태 때문에 거부될 때는 본문에 `reason`을 더한다. 상태: 확정(2026-10-27)

| 상태 | 코드 | 기능 | 뜻 / `reason` |
|------|------|------|---------------|
| 400 | `INVALID_TOKEN` | 002 | 알 수 없는 인증 토큰 |
| 401 | `UNAUTHENTICATED` | 002 | 로그인 필요 |
| 401 | `INVALID_CREDENTIALS` | 002 | 이메일/비밀번호 불일치(계정 존재 여부를 숨김) |
| 403 | `ORGANIZER_NOT_ALLOWED` | 004 | 개설자는 직접 참여 불가(수정·취소를 쓸 것) |
| 403 | `ORGANIZER_ONLY` | 006 | 개설자만 가능 |
| 404 | `PARTICIPATION_NOT_FOUND` | 004·006 | 참여 없음. 취소된 공구는 어떤 API에서도 404 |
| 409 | `NOT_MODIFIABLE` | 001 | 수정·취소 불가. `reason`: `COMPLETED` / `DEADLINE_PASSED` |
| 409 | `NOT_JOINABLE` | 004 | 참여·변경·취소 불가. `reason`: `DEADLINE_PASSED` / `COMPLETED` / `FAILED` |
| 409 | `NOT_CHECKABLE` | 006 | 모집 완료가 아니라 체크 불가 |
| 409 | `DUPLICATE_REQUEST` | 001 | 중복 개설 |
| 409 | `ALREADY_JOINED` / `PRICE_CHANGED` / `INSUFFICIENT_QUANTITY` / `BUSY` | 004 | 참여 충돌 |
| 409 | `ALREADY_REGISTERED` / `DISPLAY_NAME_TAKEN` / `TOKEN_USED` | 002 | 가입 충돌 |
| 410 | `TOKEN_EXPIRED` | 002 | 인증 링크 만료 |
| 429 | `TOO_MANY_REQUESTS` / `LOGIN_LOCKED` | 002 | 메일 한도 / 로그인 잠금 (`Retry-After`) |

  이미 참여한 사람의 같은 수량 재시도는 공구 상태와 무관하게 성공이며, `FAILED` 공구의 옛 참여 재시도는 409 `NOT_JOINABLE`(`reason: FAILED`)이다. 상태: 확정(2026-10-27)

### 6.2 시간

- DB는 UTC `DATETIME(6)`, 엔티티는 `Instant`, API는 오프셋 포함 ISO-8601이다. 사용자 입력과 표시는 KST이고 변환은 프론트가 한다.
- 시간에 의존하는 로직은 주입한 `java.time.Clock`을 쓴다. 테스트는 `MutableClock`으로 시간을 움직인다. 상태: 확정(001 plan)

### 6.3 이벤트와 알림

- 서비스는 상태가 바뀌면 Spring 이벤트를 발행하고 `AFTER_COMMIT`에서 소비한다. 007이 소비자를 붙인다. 상태: 확정(001 plan)
- 이벤트: `GroupBuyChanged`, `GroupBuyCancelled`, `GroupBuyCompleted`(001 수정·004 참여), `GroupBuyFailed`(005). 상태: 확정
- 한계: 커밋 후 프로세스가 죽으면 이벤트가 유실된다. 신뢰성이 필요해지면 아웃박스를 둔다.

### 6.4 동시성과 트랜잭션

- **공구 행이 수량·상태의 단일 진실 원천**이다. 수량이나 상태를 바꾸는 모든 쓰기(001 수정·취소, 004 참여, 005 마감 확정)는 같은 공구 행을 대상으로 직렬화되어야 한다. 상태: 확정(001 plan, 사용자 결정)
- 001 수정·취소는 `SELECT ... FOR UPDATE`로 직렬화한다. 004의 방식은 헌법 II의 4방식 비교(락 없음, 비관적, 낙관적, 조건부 UPDATE)로 정한다. 어떤 방식이 이기든 같은 행의 `secured_quantity`와 `status` 위에서 성립해야 한다. 상태: 확정/비교 예정
- 상태 변화(`OPEN → COMPLETED/CANCELLED/FAILED`)는 엔티티의 메서드 한 곳에서만 일어난다. 상세는 domain.md. 상태: 기본값
- DB `CHECK`를 최후 방어선으로 둔다(수량 범위, 가격 범위). 상태: 확정
- 마감 판정 시각은 요청을 처리하는 순간의 앱 `Clock`이다. 마감 확정(005)만 마감 시각 + 1초 이후에 처리해 시계 오차를 막는다. 상태: 확정
- 참여 경쟁의 4방식은 `QuantityGate`(004) 뒤에 숨는다. 어떤 방식이든 같은 `group_buy` 행의 `secured_quantity`·`status`·`version` 위에서 001·005와 직렬화된다. 상태: 확정

### 6.5 보안

- 임시 인증(`X-Member-Id`)은 002에서 제거됐다(쿠키 세션으로 교체, 2026-10-28 구현 완료). 단 **공개 배포와 실제 공구 운영은 002 병합 + SMTP 계정 + 허용 이메일 도메인(`ALLOWED_EMAIL_DOMAINS`) + `ALLOWED_ORIGINS`(프록시 뒤 http 종료 시) 설정 뒤에** 한다. 상태: 확정(2026-10-28, 사용자 결정)
- 오픈채팅 링크는 모집 완료 후 참여자에게만, 참여자 명단은 개설자에게만 공개한다. 상태: 확정(사용자 결정 G07, spec FR-017)
- 시크릿(DB 비밀번호 등)은 환경변수로만 쓰고 저장소에 넣지 않는다. 저장소가 공개다. 상태: 확정

### 6.6 테스트

- 핵심 로직(수량 초과, 모집 완료 전환, 중복·동시 요청, 마감 확정, 스케줄러 중복 방지)은 Testcontainers(MySQL) 통합 테스트가 필수다. H2는 쓰지 않는다. 상태: 확정(헌법 IV)
- 측정 결과(쿼리 수 전후, k6 수치)는 저장소 문서로 남긴다. 상태: 확정(헌법 II)

### 6.7 작업 규약

- 기능마다 브랜치 `NNN-feature-name`, 커밋 메시지는 `docs:`, `feat:`, `chore:` 같은 접두사 + 요약. 커밋 이메일은 GitHub noreply를 쓴다. 상태: 기본값
- 설계 결정은 plan의 `research.md`에 대안과 이유를 적고, 구현 전에 직접 설명한다(헌법 III). 상태: 확정

## 7. 기능 간 맞물리는 지점

| 지점 | 관련 기능 | 규칙 |
|------|-----------|------|
| 확보 수량 갱신 | 001, 004 | 개설자 수량 수정(001)과 참여·취소·수량 변경(004)은 모두 `secured_quantity`와 참여 행을 같은 트랜잭션에서 함께 갱신한다 |
| 모집 완료 전환 | 001, 004 | `secured == total`이 되는 순간 `completeIfFull()`을 같은 트랜잭션에서 호출한다. 중복 전환 금지 |
| 마감 확정 | 005 | `status = OPEN`이고 `deadline`이 지난 공구를 `FAILED`로 바꾼다. 001 수정·취소와 같은 공구 행 직렬화를 따른다. 여러 인스턴스에서 중복 실행되지 않아야 한다 |
| 알림 | 001, 004, 005, 007 | 각 기능은 이벤트만 발행한다. 전달은 007 |
| 조회 | 003, 006 | 목록은 공개, 상세는 로그인 필수, 명단은 개설자만 |

## 8. 아직 정해지지 않은 것

| 항목 | 정할 시점 |
|------|-----------|
| 인증 상태 유지 방식(세션/토큰), 메일 발송 수단 | 002 plan |
| 알림 저장·조회 설계 (알림함) | 007 plan |
| 목록 페이지네이션 방식 | 003 plan |
| `FAILED` 상태의 정확한 이름과 전이 | domain.md(단계 3) |
| 프론트 스택, 프론트 저장소 위치 | 4단계 |
| 배포 대상 | 4단계 |
| Flyway 도입 시점 | 5단계 전 |

## 9. 스키마 누적 변경 (`schema.sql`)

기능마다 같은 파일을 고치므로 누락을 막으려고 한곳에 모은다. 구현 순서는 001 → 003 → 004 → 005다.

| 기능 | 변경 |
|------|------|
| 001 | 테이블 `member`, `group_buy`(카테고리, 구매처 링크, 오픈채팅 링크, `version`, `CHECK` 포함), `participation`(`UNIQUE (group_buy_id, member_id)`), `create_guard` |
| 003 | 인덱스 `participation (member_id, group_buy_id)`, `group_buy (organizer_id, id)` |
| 004 | 없음 (`group_buy`·`participation` 그대로 사용) |
| 005 | 인덱스 `group_buy (status, deadline)`, 상태 값 `FAILED` (컬럼 변경 없음) |
