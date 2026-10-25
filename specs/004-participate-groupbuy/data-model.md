# Data Model: 공구 참여·수량 변경·참여 취소

**새 테이블과 컬럼은 없다.** 001의 `group_buy`와 `participation`을 그대로 쓴다. 이 문서는 이 기능이 지키는 규칙과 검증 방법을 적는다.

## 쓰는 테이블

| 테이블 | 이 기능에서의 역할 |
|--------|--------------------|
| `group_buy` | `secured_quantity`, `status`, `version`, `updated_at`을 바꾼다 |
| `participation` | 참여 시 삽입, 수량 변경 시 `quantity` 수정, 참여 취소 시 삭제(D3) |
| `member` | 존재 확인만 |

## 쓰기 규칙

세 동작 모두 **한 트랜잭션**에서 (1) `group_buy` 갱신(게이트), (2) `participation` 변경, (3) 모집 완료면 이벤트 발행을 한다. 둘 중 하나가 실패하면 모두 롤백된다.

| 동작 | `group_buy` | `participation` |
|------|-------------|-----------------|
| 참여 | `secured += q`, 가득 차면 `COMPLETED`, `version += 1` | 행 삽입 `(group_buy_id, member_id, q)` |
| 수량 변경 | `secured += (새 − 기존)`, 가득 차면 `COMPLETED`, `version += 1` | `quantity = 새` |
| 참여 취소 | `secured -= 기존`, `version += 1` | 행 삭제 |

- `version`은 모든 쓰기에서 올린다. 낙관적 방식이 충돌을 감지하고, 직접 `UPDATE`를 쓰는 방식도 같은 규칙을 따라야 섞여도 안전하다.
- 개설자의 참여 행은 이 기능이 만들지도 지우지도 바꾸지도 않는다(001 수정만 바꾼다, D4).

## 지키는 불변식

[domain.md](../../docs/domain.md) 4절 중 이 기능이 직접 책임지는 것.

| # | 불변식 | 지키는 곳 |
|---|--------|-----------|
| I1 | `secured_quantity = Σ participation.quantity` | 위 쓰기 규칙(같은 트랜잭션) |
| I2 | `1 ≤ secured ≤ total` | 게이트 조건 + DB `CHECK` |
| I3 | `COMPLETED` 이후 확보 수량 불변 | 게이트 조건 `status = 'OPEN'` |
| I4 | 개설자의 참여는 이 기능으로 건드리지 않는다 | 서비스가 개설자 요청을 403으로 거부 |
| I5 | 한 회원은 한 공구에 참여 행 최대 하나 | DB `UNIQUE (group_buy_id, member_id)` |
| I7 | 상태 전이는 `OPEN`에서만 | 게이트 조건 |

## 불변식 검증 쿼리 (측정·테스트용)

I1 위반 공구를 찾는다. 결과가 0행이면 정상이다.

```sql
SELECT g.id, g.secured_quantity, COALESCE(SUM(p.quantity), 0) AS actual
FROM group_buy g LEFT JOIN participation p ON p.group_buy_id = g.id
GROUP BY g.id, g.secured_quantity
HAVING g.secured_quantity <> COALESCE(SUM(p.quantity), 0);
```

I2 위반(초과)은 `WHERE secured_quantity > total_quantity`로 찾는다. 단 `CHECK`가 있어 실제 값은 저장되지 않고 오류가 난다. 오류 건수는 앱 지표로 센다.

## 이벤트

| 이벤트 | 발행 조건 | 필드 |
|--------|-----------|------|
| `GroupBuyCompleted` | 게이트 결과가 모집 완료 | `groupBuyId`, `recipientIds`(개설자 포함 모든 참여 행의 회원 id) |

001의 `GroupBuyEvents.java`에 record로 추가한다.
