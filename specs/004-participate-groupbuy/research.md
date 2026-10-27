# Research: 공구 참여·수량 변경·참여 취소

NEEDS CLARIFICATION은 없다. 결정 1~3은 사용자가 직접 고른 것이고, 4 이후는 기본값이다(바꾸고 싶으면 알려줄 것).
헌법 III에 따라 각 결정은 구현 전에 사용자가 설명할 수 있어야 한다.

## 사용자 결정

### 1. 4방식은 설정 스위치로 모두 코드에 남긴다

- **Decision**: `QuantityGate` 인터페이스에 구현체 4개(`NoLock`, `Pessimistic`, `Optimistic`, `ConditionalUpdate`)를 두고 `camgong.quantity-gate` 설정 값으로 고른다. k6는 같은 시나리오를 방식만 바꿔 다시 돌린다.
- **Rationale**: 측정 결과를 저장소를 받은 누구나 재현할 수 있다. 구현체가 넷이라 인터페이스가 정당화된다.
- **Alternatives**: (a) 하나씩 구현·측정 후 교체, 방식별 태그로 보존 — 코드는 짧지만 재측정하려면 태그를 체크아웃해야 한다. (b) 최종 방식만 main, 나머지는 측정용 브랜치 — 깔끔하지만 비교 시점의 코드가 흩어진다.
- **설명 체크**: 왜 이 구조에서만 4방식 차이가 한 클래스 안에 갇히는가?

### 1-1. 운영 기본 방식은 pessimistic으로 확정 (2026-10-28 사용자 결정)

측정(docs/benchmarks/participation-concurrency.md, 한 대 기계에서 앱·DB·k6 동거, 혼합 부하는 30초×5회)에서 `none`은 마지막 자리 100명 동시 30회가 모두 깨졌고(갱신 손실), pessimistic·conditional·optimistic은 초과 0·완료 이벤트 1회였다. pessimistic과 conditional은 지연이 같고(S1 p95 70.0/75.7ms), optimistic은 같은 행에 쓰기가 몰리는 혼합 부하에서 요청의 약 50%가 BUSY로 거부되어 이 서비스에 부적합하다. 001 수정·취소·005 마감 확정과 같은 락 모델(공구 → 참여 순서)이라 설명이 쉬운 **pessimistic을 기본으로 둔다.** 다른 방식은 설정 스위치로 코드에 남긴다(결정 1).

### 2. 참여 API는 리소스형이다

- **Decision**: `POST /api/groupbuys/{id}/participations`(참여), `PUT /api/groupbuys/{id}/participations/me`(수량 변경), `DELETE /api/groupbuys/{id}/participations/me`(참여 취소). 상세는 [contracts/participation-api.md](contracts/participation-api.md).
- **Rationale**: 참여는 공구 하위의 자원이고, 참여 취소는 기록을 지우므로 `DELETE`가 맞다(도메인 결정 D3). 001의 `PUT`, 자원 중심 설계와 일관된다.
- **Alternatives**: 동사형(`/join`, `/leave`) — 의도는 바로 보이지만 REST 규칙에서 벗어나고 경로가 늘어난다.
- **설명 체크**: 001에서 공구 취소를 `DELETE`가 아닌 `POST .../cancel`로 한 것과 여기서 참여 취소를 `DELETE`로 한 것이 왜 다른가?

### 3. 마감 판정 시각은 앱 `Clock`, 확정만 안전 여유 1초

- **Decision**: 참여·수량 변경·참여 취소·수정·취소는 모두 요청을 처리하는 순간의 `Instant.now(clock)`으로 마감을 판정한다(005 결정: 처리 시각 기준). 마감 확정(005)은 `마감 시각 + 1초 ≤ 현재`인 공구만 처리한다.
- **Rationale**: 001 plan의 주입 `Clock`을 그대로 써서 테스트에서 시간을 움직일 수 있다. 서버 시계가 동기화되었다고 가정하되, 인스턴스 간 오차로 마감 전 공구가 실패로 확정되는 일(005 FR-010)을 1초 여유로 막는다. 대가로 마감~확정 간격이 최대 1초 늘지만 목표 1분 안이다.
- **Alternatives**: DB 시각(`NOW(6)`) — 모든 인스턴스가 같은 기준을 쓰지만 테스트에서 시간 조작이 어렵고 001 설계를 바꿔야 한다. 앱 Clock만, 여유 없음 — 가장 단순하지만 시계 오차 시 오확정 위험이 있다.
- **설명 체크**: 마감 직전에 도착했지만 처리 시각이 마감 이후인 참여 요청은 왜 거부되고, 그것이 005와 어떻게 모순되지 않는가?

## 기본값 결정

### 4. 게이트 계약과 책임 분리

- **Decision**: `QuantityGate.reserve(groupBuyId, delta, expectedUnitPrice, now)`는 `group_buy` 행에 대해 다음을 한 번에 처리한다: 존재·취소 아님, `OPEN`, `deadline > now`, (참여·변경이면) 개당 가격 일치, `secured + delta ≤ total`, 그리고 `secured` 갱신과 `version` 증가, `secured = total`이면 `COMPLETED` 전환. 결과는 `GateResult`(성공이면 갱신 후 확보 수량과 모집 완료 여부, 거부면 사유). 참여 행 삽입·수정·삭제와 이벤트는 `ParticipationService`가 같은 트랜잭션에서 한다.
  - 참여: `delta = +수량`, 변경: `delta = 새 수량 − 기존 수량`, 취소: `delta = −기존 수량`(가격 비교 없음, `expectedUnitPrice = null`).
- **Rationale**: 4방식이 다르게 구현하는 것은 "수량 예약" 한 가지다. 나머지를 서비스에 두면 방식 간 비교가 공정하다(같은 일을 다른 방법으로).
- **Alternatives**: 게이트가 참여 행까지 처리 — 방식마다 중복 코드가 늘고 차이가 흐려진다.
- **설명 체크**: 참여 행 삽입이 실패(중복)하면 이미 올라간 `secured`는 어떻게 되돌려지는가?

### 5. 방식별 구현 요지

| 방식 | 요지 | 기대 |
|------|------|------|
| `none` | 일반 `SELECT`로 읽고 자바에서 검사한 뒤 `secured = 읽은 값 + delta`로 쓴다 | 갱신 손실로 `secured ≠ Σ 수량`이 생긴다(불변식 I1 위반). DB `CHECK`가 일부 초과를 오류로 바꾼다 |
| `pessimistic` | `SELECT ... FOR UPDATE`로 행을 잡고 검사·갱신 | 정확. 경쟁이 심하면 대기 시간이 늘어난다 |
| `optimistic` | `@Version`으로 읽고 갱신 시 `WHERE version = ?`. 충돌하면 최대 3회, 짧은 무작위 대기 후 재시도. 소진하면 409 `BUSY` | 정확. 충돌이 많으면 실패·재시도가 늘어난다 |
| `conditional` | 한 문장 `UPDATE ... WHERE status='OPEN' AND deadline>? AND unit_price=? AND secured+?<=total`. 영향 행 0이면 사유를 `SELECT`로 진단 | 정확. 왕복이 가장 적다 |

`conditional`의 갱신문은 스파이크로 확인했다([docs/spikes.md](../../docs/spikes.md) #10, #11).

```sql
UPDATE group_buy SET
  secured_quantity = secured_quantity + :delta,
  status = IF(secured_quantity = total_quantity, 'COMPLETED', status),
  version = version + 1, updated_at = :now
WHERE id = :id AND status = 'OPEN' AND deadline > :now
  AND unit_price = :expectedUnitPrice          -- 취소는 이 조건 생략
  AND secured_quantity + :delta <= total_quantity
```

MySQL은 `SET` 절을 왼쪽에서 오른쪽으로 평가하므로 두 번째 줄의 `secured_quantity`는 이미 갱신된 값이다.

### 6. 001·005와의 직렬화는 행 락 하나로 맞춘다

- **Decision**: 001 수정·취소와 005 확정은 `FOR UPDATE`로 공구 행을 잡는다. 참여 방식 4개 중 `pessimistic`, `conditional`은 같은 행 락으로 자연히 직렬화되고, `optimistic`은 모든 쓰기가 `version`을 올리므로 충돌을 감지한다. `none`은 보호가 없다(비교용).
- **Rationale**: 어떤 방식이 이기든 같은 `group_buy` 행의 `secured_quantity`·`status`·`version` 위에서 성립한다(architecture 6.4).
- **설명 체크**: 개설자의 수정(락 보유)과 낙관적 방식의 참여가 겹치면 누가 기다리고 누가 재시도하는가?

### 7. 멱등 재시도와 중복 제약

- **Decision**: 참여는 (1) 요청자가 개설자면 403, (2) 이미 참여 행이 있고 수량이 같으면 변화 없이 성공(200), 다르면 409 `ALREADY_JOINED`, (3) 없으면 게이트 예약 후 행 삽입. 같은 회원의 동시 이중 요청은 `UNIQUE (group_buy_id, member_id)` 위반으로 한 쪽의 트랜잭션이 롤백되며(예약도 함께 되돌림), 서비스가 한 번 다시 (2)부터 처리해 수량이 같으면 성공을 돌려준다.
- **Rationale**: spec FR-004와 SC-006. 예약과 삽입이 한 트랜잭션이라 중간 상태가 남지 않는다.

### 8. 측정 설계 (헌법 II)

- **시나리오**:
  - S1 마지막 자리: 남은 수량 1, 서로 다른 회원 100명이 동시에 1개씩 참여.
  - S2 다양한 수량: 남은 수량 10, 50명이 1~5개를 무작위로 요청.
  - S3 혼합: 참여·수량 변경·참여 취소와 개설자 수정을 섞어 60초 부하.
- **지표**: 성공 수와 거부 수(사유별), 초과 건수(`secured > total`)와 불변식 I1 위반 건수, 모집 완료 전환 횟수, p50/p95/p99 응답 시간, 처리량, 재시도 횟수(낙관적), 오류율.
- **고정 변수**: 같은 머신의 Docker MySQL 8.4(기본 격리 수준 REPEATABLE READ), 앱 JVM 힙과 DB 커넥션 풀 크기(20)를 고정하고 결과에 함께 기록한다. 시나리오당 30회 반복해 분포를 낸다.
- **정확성과 성능을 분리한다**: SC-002의 "1,000회 반복"은 스레드 기반 통합 테스트(`QuantityRaceIT`)가 맡고, k6는 성능·분포를 맡는다.
- **불변식 검증 쿼리**: 시나리오 직후 [data-model.md](data-model.md)의 검증 쿼리를 실행해 위반이 0인지 기록한다.
- **산출물**: `docs/benchmarks/participation-concurrency.md`(표와 해석). 최종 운영 방식은 결과를 보고 그때 정한다.

### 9. `none` 방식의 안전장치

- **Decision**: `none`은 `benchmark` 프로파일에서만 켤 수 있고, 다른 프로파일에서 선택하면 앱이 기동 오류를 낸다. 기본 방식은 측정 전까지 `pessimistic`이다.
- **Rationale**: 일부러 틀리게 만든 구현이 운영에 섞이는 사고를 막는다. 기본값을 안전한 쪽으로 둔다.

### 10. 모집 완료 이벤트

- **Decision**: 게이트 결과가 모집 완료이면 서비스가 같은 트랜잭션에서 개설자를 포함한 모든 참여 행의 회원 id로 `GroupBuyCompleted(groupBuyId, recipientIds)`를 발행하고 커밋 후(`AFTER_COMMIT`)에 소비한다. 참여·수량 변경·참여 취소 자체는 이벤트를 발행하지 않는다(spec FR-012).
- **한계**: 005와 같다. 커밋 직후 프로세스가 죽으면 유실될 수 있다.

### 11. 오류 매핑

| 사유 | 상태 | 코드(title) |
|------|------|-------------|
| 입력 형식 오류(수량 ≤ 0, 정수 아님) | 400 | `errors[]` |
| 공구 없음·취소됨 | 404 | |
| 참여 기록 없음(변경·취소) | 404 | `PARTICIPATION_NOT_FOUND` |
| 개설자 요청 | 403 | `ORGANIZER_NOT_ALLOWED` |
| 이미 참여(수량 다름) | 409 | `ALREADY_JOINED` |
| 모집 중 아님(완료·실패) 또는 마감 지남 | 409 | `NOT_JOINABLE` |
| 개당 가격 불일치 | 409 | `PRICE_CHANGED` (본문에 `currentUnitPrice`) |
| 남은 수량 부족 | 409 | `INSUFFICIENT_QUANTITY` (본문에 `remainingQuantity`) |
| 낙관적 재시도 소진 | 409 | `BUSY` |

001의 `ProblemDetail` + `errors[]` 형식을 따른다.

## 미검증 가정 (구현 시작 직전에 확인)

| # | 가정 | 확인 방법 |
|---|------|-----------|
| 1 | REPEATABLE READ에서 `none` 방식이 실제로 갱신 손실을 일으킨다 | 소규모 동시 실행으로 관찰 |
| 2 | HikariCP 풀 크기(20)가 100 동시 요청 비교에서 병목이 되지 않는다 | 풀 대기 시간 지표 확인 |
| 3 | 낙관적 방식의 `@Version` 갱신이 `conditional`의 직접 `UPDATE`(엔티티 바이패스)와 같은 버전 규칙으로 맞물린다 | 혼합 통합 테스트 |
