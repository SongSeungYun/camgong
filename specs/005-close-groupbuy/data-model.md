# Data Model: 마감 확정(모집 실패 처리)

**새 테이블은 없다.** 001의 `group_buy`에 상태 값과 인덱스를 더하고, `participation`은 읽기만 한다.

## 변경

| 대상 | 변경 | 비고 |
|------|------|------|
| `group_buy.status` | 값 `FAILED` 추가 (`OPEN`, `COMPLETED`, `CANCELLED`, `FAILED`) | 컬럼은 `VARCHAR(20)`이라 스키마 변경 없음. 코드의 `GroupBuyStatus`에 추가 |
| `group_buy` 인덱스 | `idx_group_buy_status_deadline (status, deadline)` 추가 | `schema.sql` 수정. 후보 조회용 |
| `participation` | 변경 없음 | 모집 실패 후에도 보존(FR-003) |

## 상태 변화

```text
OPEN ──(deadline <= now−1초 이고 secured < total, 005)──► FAILED  (종료)
```

- 가드: `status = 'OPEN'`, `deadline <= :cutoff(now − 1초)`, `secured_quantity < total_quantity`. 세 조건을 락을 잡은 뒤 다시 확인한다.
- `FAILED`에서 나가는 전이는 없다. `COMPLETED`, `CANCELLED`는 대상이 아니다.
- 엔티티 메서드 `GroupBuy.fail(now)`가 전이의 유일한 경로이며, 가드를 어기면 예외를 던진다(불변식 I7).
- `updated_at`은 `Instant.now(clock)`으로, `version`은 JPA가 증가시킨다(004의 낙관적 방식과 맞물림).

## 쓰는 쿼리

후보 조회 (락 없음):

```sql
SELECT id FROM group_buy
WHERE status = 'OPEN' AND deadline <= :cutoff AND secured_quantity < total_quantity
ORDER BY deadline LIMIT :batchSize
```

선점 (건별, 결정 1):

```sql
SELECT * FROM group_buy
WHERE id = :id AND status = 'OPEN' AND deadline <= :cutoff AND secured_quantity < total_quantity
FOR UPDATE SKIP LOCKED
```

참여자 id (이벤트 대상): `SELECT member_id FROM participation WHERE group_buy_id = :id` — 개설자 포함.

## 불변식

[domain.md](../../docs/domain.md) 4절 중 이 기능이 책임지는 것.

| # | 불변식 | 지키는 곳 |
|---|--------|-----------|
| I7 | 상태 전이는 `OPEN`에서만 | `GroupBuy.fail()` 가드 + 선점 쿼리의 `status='OPEN'` |
| I10 | `FAILED`는 마감이 지났고 확보 < 총 수량일 때만 | 선점 쿼리 조건 |
| I3 | `COMPLETED`는 확정 대상이 아니다 | `secured_quantity < total_quantity` 조건 |
| (005) | 같은 공구는 한 번만 확정, 이벤트도 한 번 | `SKIP LOCKED` 선점 후 재확인, 같은 트랜잭션에서 이벤트 |

## 이벤트

| 이벤트 | 발행 조건 | 필드 |
|--------|-----------|------|
| `GroupBuyFailed` | 공구가 `FAILED`로 확정되어 커밋됨 | `groupBuyId`, `recipientIds`(개설자 포함 모든 참여자) |

001의 `GroupBuyEvents.java`에 record로 추가하고 `GroupBuyEventLogger`가 로그로 소비한다. 실제 전달은 알림 기능(007).

## 검증 쿼리 (테스트용)

잘못 확정된 공구를 찾는다. 결과가 0행이어야 한다.

```sql
-- 마감 전인데 FAILED, 또는 가득 찼는데 FAILED
SELECT id FROM group_buy
WHERE status = 'FAILED' AND (deadline > :asOf OR secured_quantity = total_quantity);
```
