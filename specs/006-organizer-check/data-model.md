# Data Model: 개설자용 참여자·입금·수령 체크

> 초안: 추천안 기준([research.md](research.md), 2026-10-26 확정).

**새 테이블은 없다.** 001의 `participation`에 컬럼 2개를 더한다.

## 변경

| 대상 | 변경 | 비고 |
|------|------|------|
| `participation.paid_at` | `DATETIME(6) NULL` 추가 | 입금 확인 시각. NULL = 확인 전 |
| `participation.received_at` | `DATETIME(6) NULL` 추가 | 수령 확인 시각. NULL = 확인 전 |

`schema.sql`은 `CREATE TABLE IF NOT EXISTS`라서 이미 만들어진 DB에는 컬럼이 안 생긴다. 운영 데이터가 생기기 전(5단계 전)에는 001의 `participation` 정의에 컬럼을 직접 넣는다. 데이터가 생긴 뒤에는 Flyway(architecture 3절의 도입 시점)로 올려야 한다.

## 불변식

| # | 불변식 | 지키는 곳 |
|---|--------|-----------|
| (006) | 체크는 모집 완료 공구의 개설자만 지정·조회한다 | 서비스 접근 검사 (`COMPLETED`는 종료 상태라 읽은 뒤에도 불변) |
| (006) | 개설자 본인의 참여 행은 체크 대상이 아니다 | 체크표 쿼리 `member_id <> organizer_id`, PUT은 400 |
| (006) | 확인 시각은 한 번 지정되면 덮어쓰지 않는다 | `COALESCE(paid_at, :now)` |
| (006) | 되돌리면 시각이 지워진다 | `SET … = NULL` |
| (006) | 입금과 수령은 서로 영향을 주지 않는다 | 필드별 별도 `UPDATE` |

## 쓰는 쿼리

체크 지정 (입금, 수령은 컬럼만 다름):

```sql
UPDATE participation
SET paid_at = COALESCE(paid_at, :now)
WHERE group_buy_id = :groupBuyId AND member_id = :memberId
  AND member_id <> (SELECT organizer_id FROM group_buy WHERE id = :groupBuyId)
```

되돌리기:

```sql
UPDATE participation SET paid_at = NULL
WHERE group_buy_id = :groupBuyId AND member_id = :memberId
  AND member_id <> (SELECT organizer_id FROM group_buy WHERE id = :groupBuyId)
```

영향 행 0은 "이미 그 상태"이거나 "대상이 아님"일 수 있어서 서비스가 먼저 참여 행의 존재를 확인해 404/400을 구분한 뒤 `UPDATE`를 실행한다.

체크표 (003 명단 쿼리 + 컬럼, 개설자 제외):

```sql
SELECT p.member_id, m.name, p.quantity, p.paid_at, p.received_at
FROM participation p JOIN member m ON m.id = p.member_id
WHERE p.group_buy_id = :id AND p.member_id <> :organizerId
  [AND p.paid_at IS NULL]        -- filter=unpaid
  [AND p.received_at IS NULL]    -- filter=unreceived
ORDER BY p.id
```

요약은 필터와 무관하게 전체 기준이다:

```sql
SELECT COUNT(*), COUNT(paid_at), COUNT(received_at),
       SUM(quantity), SUM(CASE WHEN paid_at IS NOT NULL THEN quantity END)
FROM participation WHERE group_buy_id = :id AND member_id <> :organizerId
```

금액은 `개당 가격 × 수량`으로 서비스가 계산한다(개당 가격은 공구에서 읽는다).

## 참여자 본인 상태 (003 상세 확장)

`myParticipation`에 `paidConfirmedAt`, `receivedConfirmedAt`(nullable)을 더한다. 값은 본인의 `participation` 행에서 읽는다. 개설자의 `myParticipation`에는 이 필드가 없다(개설자는 체크 대상이 아니다).
