---

description: "공구 참여·수량 변경·참여 취소 구현 태스크"
---

# Tasks: 공구 참여·수량 변경·참여 취소

**Input**: `/specs/004-participate-groupbuy/` 의 plan.md, spec.md, research.md, data-model.md, contracts/participation-api.md, quickstart.md

**Prerequisites**: plan.md, spec.md. **001 구현(US1, US3의 `completeIfFull`·`GroupBuyCompleted` 포함)이 먼저 끝나 있어야 한다**: `GroupBuy`(`secured_quantity`, `version`, 상태 전이), `Participation`, `ParticipationRepository`, `GroupBuyRepository.findByIdForUpdate`, `GroupBuyEvents`(`GroupBuyCompleted`), `ApiException`, `IntegrationTestBase`(`MutableClock`), 임시 인증. 003·005·002는 필요 없다.

**Tests**: 헌법 IV의 필수 대상 — 수량 초과 판정, 모집 완료 전환, 동시 참여 경쟁 — 의 Testcontainers(MySQL) 통합 테스트를 포함한다. 헌법 II에 따라 4방식은 k6로 비교한다. 각 스토리에서 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.

**Organization**: 사용자 스토리별로 묶었다. 이 프로젝트는 `implement`를 쓰지 않고 사용자가 직접 구현한다. **(핵심 로직)** 표시는 락·쿼리·상태 전이를 다루므로 직접 쓴다. 헌법 II 비교의 본체라 이 기능은 핵심 로직의 비중이 크다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 태스크에 의존하지 않아 병렬 가능
- **[Story]**: US1(참여), US2(마지막 수량 경쟁), US3(참여 취소), US4(수량 변경)
- 경로의 `main/`은 `src/main/java/com/camgong/`, `test/`는 `src/test/java/com/camgong/`, `res/`는 `src/main/resources/` 를 뜻한다. 이 기능의 새 코드는 하위 패키지 `groupbuy/join/`에 둔다. `Participation`, `ParticipationRepository`는 001의 `groupbuy/`에 있다.

## Phase 1: Setup

**Purpose**: 요청·응답·게이트 계약, 테스트 시드

- [ ] T001 [P] `main/groupbuy/join/ParticipationRequest.java`: record `quantity`(`@NotNull @Min(1)`), `expectedUnitPrice`(`@NotNull @Min(1)`). 수량의 상한은 요청 검증이 아니라 처리 시점의 남은 수량으로 판정한다.
- [ ] T002 [P] `main/groupbuy/join/ParticipationResponse.java`: record — `groupBuyId`, `quantity`, `amount`(= 개당 가격 × quantity), `securedQuantity`, `remainingQuantity`, `status`(`OPEN`/`COMPLETED`). 본인 정보만 담는다(spec FR-014).
- [ ] T003 [P] `main/groupbuy/join/GateResult.java`: 성공(갱신 후 `securedQuantity`, `completed`)과 거부 사유(`NOT_FOUND`, `NOT_JOINABLE`, `PRICE_CHANGED`(+`currentUnitPrice`), `INSUFFICIENT_QUANTITY`(+`remainingQuantity`), `BUSY`)를 표현한다.
- [ ] T004 `main/groupbuy/join/QuantityGate.java`: 인터페이스 `GateResult reserve(long groupBuyId, int delta, Integer expectedUnitPrice, Instant now)`. 취소는 `expectedUnitPrice = null`. 게이트는 `group_buy` 행(상태·마감·가격·수량)만 책임지고 참여 행과 이벤트는 건드리지 않는다.
- [ ] T005 [P] `test/support/IntegrationTestBase.java`에 시드 헬퍼 추가: 회원 2~101(참여자 100명), 총 24·개설자 수량 9·개당 1,200원·마감 +7일 공구를 만드는 메서드, `participation` 행을 직접 삽입하며 `secured_quantity`를 맞추는 메서드. 동시 실행에 쓸 스레드 풀 도우미.
- [ ] T006 [P] `main/groupbuy/ParticipationRepository.java`에 메서드 추가: (공구, 회원)으로 참여 1건, 공구의 모든 참여자 회원 id(개설자 포함), (공구, 회원) 삭제.

---

## Phase 2: Foundational (모든 스토리의 선행 조건)

**Purpose**: 기본 게이트(비관적)와 서비스 뼈대, 오류 매핑

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 스토리 작업을 시작하지 않는다.

- [ ] T007 `main/groupbuy/join/PessimisticQuantityGate.java` **(핵심 로직)**: `GroupBuyRepository.findByIdForUpdate`로 행을 잡고 아래 순서로 검사한 뒤 갱신한다 — 없거나 `CANCELLED`면 `NOT_FOUND`, `status != OPEN` 또는 `deadline <= now`면 `NOT_JOINABLE`, `expectedUnitPrice`가 있고 현재 가격과 다르면 `PRICE_CHANGED`, `secured + delta > total`이면 `INSUFFICIENT_QUANTITY`. 통과하면 `secured_quantity += delta`, `completeIfFull()`로 `COMPLETED` 전환, `updatedAt = now`. 이 게이트가 측정 전까지 기본 방식이다.
- [ ] T008 `main/groupbuy/join/ParticipationService.java` 뼈대와 오류 매핑: 회원 존재 확인(없으면 401), 거부 사유를 `ApiException`으로 변환 — `NOT_FOUND` 404, `NOT_JOINABLE` 409 `NOT_JOINABLE`, `PRICE_CHANGED` 409(본문에 `currentUnitPrice`), `INSUFFICIENT_QUANTITY` 409(본문에 `remainingQuantity`), `BUSY` 409 `BUSY`, 개설자 요청 403 `ORGANIZER_NOT_ALLOWED`, 참여 기록 없음 404 `PARTICIPATION_NOT_FOUND`, 이미 참여(수량 다름) 409 `ALREADY_JOINED`. `ApiExceptionHandler`가 추가 본문 속성을 `ProblemDetail`에 실을 수 있게 확장한다.
- [ ] T009 `main/groupbuy/join/QuantityGateConfig.java`: `camgong.quantity-gate`(`pessimistic` 기본, `conditional`, `optimistic`, `none`)로 구현체를 고르는 빈. `none`은 `benchmark` 프로파일에서만 허용하고 다른 프로파일에서 선택하면 앱이 기동 오류를 낸다. (구현체는 해당 스토리에서 추가하며, 아직 없는 값은 기동 오류로 처리한다.)

**Checkpoint**: 비관적 게이트와 서비스 뼈대가 컴파일되고 앱이 `pessimistic`으로 뜬다.

---

## Phase 3: User Story 1 - 공구에 참여하기 (Priority: P1) 🎯 MVP

**Goal**: 개설자가 아닌 로그인 회원이 수량을 정해 참여하고, 가득 차면 모집 완료가 된다. 재시도는 안전하다.

**Independent Test**: 총 24·확보 9인 공구에 회원이 6개로 참여해 확보 15, 남은 9, 부담 금액 7,200원을 확인하고, 남은 9를 채우면 모집 완료가 된다.

### Tests for User Story 1 ⚠️ (먼저 작성하고 실패 확인)

- [ ] T010 [P] [US1] `test/groupbuy/join/JoinParticipationIT.java`: quickstart 시나리오 1(정상: 201, `amount=7200`, `securedQuantity=15`, `remainingQuantity=9`, `OPEN`), 2(딱 채우기: `COMPLETED`, 이후 참여 409 `NOT_JOINABLE`), 3(남은 수량 초과 → 409 `INSUFFICIENT_QUANTITY`, `remainingQuantity`), 6(개설자 403), 7(헤더 없음 401), 8(개당 가격 변경 후 구 가격으로 참여 → 409 `PRICE_CHANGED`, `currentUnitPrice`), 14(`COMPLETED`·마감 경과 후 참여 409), 15(`Clock`을 마감 정각으로 → 409), 20(마지막 수량 참여 시 `GroupBuyCompleted` 정확히 1건, 대상은 개설자 포함 전원; 일반 참여는 이벤트 없음)
- [ ] T011 [P] [US1] `test/groupbuy/join/IdempotentJoinIT.java`: 시나리오 4(같은 수량으로 다시 참여 → 200, 변화 없음), 5(다른 수량으로 다시 참여 → 409 `ALREADY_JOINED`), 마지막 수량 참여의 응답을 못 받고 같은 요청을 재시도(공구가 이미 `COMPLETED`여도 같은 수량이면 200으로 성공을 돌려준다)

### Implementation for User Story 1

- [ ] T012 [US1] `main/groupbuy/join/ParticipationService.java`의 `join` **(핵심 로직)**: `@Transactional`. 순서: (1) 개설자면 403, (2) 이미 참여 행이 있으면 수량이 같을 때만 200(상태·마감과 무관하게 현재 상태로 응답), 다르면 409 `ALREADY_JOINED`, (3) 게이트 `reserve(+quantity, expectedUnitPrice, now)`, (4) 참여 행 삽입, (5) `completed`면 개설자 포함 모든 참여 행의 회원 id로 `GroupBuyCompleted` 발행. `UNIQUE (group_buy_id, member_id)` 위반(같은 회원의 동시 이중 요청)은 트랜잭션을 롤백(예약도 함께 되돌림)하고 한 번 다시 처리해 (2)로 들어가게 한다. 시각은 `Instant.now(clock)`.
- [ ] T013 [US1] `main/groupbuy/join/ParticipationController.java`: `POST /api/groupbuys/{id}/participations`(`@Valid` 본문, 새 참여 201, 재시도 성공 200, `X-Member-Id` 임시 인증). 응답은 `ParticipationResponse`.

**Checkpoint**: US1이 단독으로 동작한다. T010·T011이 통과한다.

---

## Phase 4: User Story 2 - 마지막 남은 수량 경쟁 (Priority: P2)

**Goal**: 여러 요청이 동시에 와도 확보 수량이 총 수량을 넘지 않고 모집 완료 전환이 한 번이다. 4방식을 구현해 비교할 수 있다.

**Independent Test**: 남은 수량 10에 여러 명이 3·4·5·6개로 동시에 참여해 성공 수량 합이 10을 넘지 않고 확보 수량이 맞는지 확인한다.

### Tests for User Story 2 ⚠️

- [ ] T014 [P] [US2] `test/groupbuy/join/QuantityRaceIT.java`: 안전한 3방식(`pessimistic`, `conditional`, `optimistic`)을 `@ParameterizedTest`로 돌린다. 시나리오 16(남은 1, 100명이 동시에 1개씩 → 201 정확히 1건, 나머지 409, `COMPLETED` 한 번, 불변식 I1 위반 0), 17(남은 10, 3·4·5·6개 동시 → 성공 수량 합 ≤ 10, `secured = 9 + 성공 합`), 18(같은 회원이 같은 요청을 동시에 2개 → 참여 행 1건), 각 시나리오를 반복(CI는 100회, 수동으로 1,000회)하며 불변식 검증 쿼리(data-model.md)를 매번 실행. `none`은 이 테스트에서 제외한다.
- [ ] T015 [P] [US2] `test/groupbuy/join/ParticipationSerializationIT.java`: 시나리오 19(개설자 취소와 참여 동시 → 먼저 처리된 쪽 기준, 취소된 공구에 참여 행 없음), 개설자 수정(총 수량·개설자 수량 변경)과 참여 동시에 `secured ≤ total` 유지. 3방식 모두에서 실행.

### Implementation for User Story 2

- [ ] T016 [US2] `main/groupbuy/join/ConditionalUpdateQuantityGate.java` **(핵심 로직)**: 한 문장 `UPDATE`로 선점한다(research.md 결정 5, `JdbcClient`·네이티브).

  ```sql
  UPDATE group_buy SET
    secured_quantity = secured_quantity + :delta,
    status = IF(secured_quantity = total_quantity, 'COMPLETED', status),
    version = version + 1, updated_at = :now
  WHERE id = :id AND status = 'OPEN' AND deadline > :now
    AND unit_price = :expectedUnitPrice          -- 취소는 이 조건 생략
    AND secured_quantity + :delta <= total_quantity
  ```

  영향 행 1이면 성공(이후 `SELECT`로 갱신 값과 `completed` 확인), 0이면 **사유를 `SELECT`로 진단**해 `NOT_FOUND`/`NOT_JOINABLE`/`PRICE_CHANGED`/`INSUFFICIENT_QUANTITY`로 매핑한다. `SET` 절 평가 순서(왼쪽에서 오른쪽)에 의존한다(spikes #10). JDBC URL의 `useAffectedRows=true`가 전제다(spikes #1).
- [ ] T017 [US2] `main/groupbuy/join/OptimisticQuantityGate.java` **(핵심 로직)**: `@Version`으로 읽고 검사한 뒤 갱신하며 `WHERE version = ?`로 충돌을 감지한다. 충돌하면 최대 3회, 짧은 무작위 대기 후 재시도하고 소진하면 `BUSY`. 모든 쓰기가 `version`을 올리는 규칙을 따른다(직접 `UPDATE`인 조건부 방식과 맞물리도록).
- [ ] T018 [US2] `main/groupbuy/join/NoLockQuantityGate.java` **(핵심 로직, 벤치마크 전용)**: 일반 `SELECT`로 읽고 자바에서 검사한 뒤 `secured = 읽은 값 + delta`로 쓴다. 갱신 손실로 `secured ≠ Σ 수량`이 생기는 것이 기대 동작이다. `benchmark` 프로파일에서만 활성화되도록 T009를 마무리한다.
- [ ] T019 [US2] `QuantityGateConfig`(T009)를 마무리: 4개 구현체를 모두 연결하고, 알 수 없는 값은 기동 오류, `none`은 `benchmark` 프로파일 외 기동 오류.

**Checkpoint**: 3방식이 `QuantityRaceIT`·`ParticipationSerializationIT`를 통과한다. `none`은 `benchmark` 프로파일에서만 뜬다.

---

## Phase 5: User Story 3 - 참여 취소하기 (Priority: P2)

**Goal**: 참여자가 마감 전에 취소하면 기록이 지워지고 공구는 유지된다.

**Independent Test**: 6개로 참여한 회원이 취소해 확보가 6 줄고, 개설자는 취소할 수 없고, 모집 완료 후엔 취소할 수 없는지 확인한다.

### Tests for User Story 3 ⚠️

- [ ] T020 [P] [US3] `test/groupbuy/join/LeaveParticipationIT.java`: 시나리오 12(204, 확보 −6, 공구 `OPEN` 유지, 이후 재참여 가능), 13(개설자 DELETE 403), 14(완료·마감 후 취소 409 `NOT_JOINABLE`), 참여 기록 없음 404 `PARTICIPATION_NOT_FOUND`, 참여 취소 때는 이벤트가 없다.

### Implementation for User Story 3

- [ ] T021 [US3] `main/groupbuy/join/ParticipationService.java`의 `leave` **(핵심 로직)**: 개설자면 403, 참여 행이 없으면 404, 게이트 `reserve(−기존 수량, null, now)` 후 참여 행 삭제(트랜잭션). 거부 사유는 T008 매핑.
- [ ] T022 [US3] `main/groupbuy/join/ParticipationController.java`에 `DELETE /api/groupbuys/{id}/participations/me`(204) 추가.

**Checkpoint**: US3이 통과한다.

---

## Phase 6: User Story 4 - 참여 수량 바꾸기 (Priority: P3)

**Goal**: 참여자가 마감 전에 자기 수량을 바꾼다.

**Independent Test**: 6개로 참여한 회원이 8개, 3개로 바꾸고 한도를 넘는 변경은 거부되는지, 딱 채우면 모집 완료가 되는지 확인한다.

### Tests for User Story 4 ⚠️

- [ ] T023 [P] [US4] `test/groupbuy/join/ChangeParticipationIT.java`: 시나리오 9(6→3: 확보 −3), 10(남은 9에서 6→16 → 409 `INSUFFICIENT_QUANTITY`), 11(6→15로 딱 채움 → `COMPLETED`, `GroupBuyCompleted` 1건), 같은 수량 변경은 200 무변화, 개설자 PUT 403(공구 수정을 쓸 것), 가격 변경 후 구 가격으로 변경 → 409 `PRICE_CHANGED`

### Implementation for User Story 4

- [ ] T024 [US4] `main/groupbuy/join/ParticipationService.java`의 `change` **(핵심 로직)**: 개설자면 403, 참여 행이 없으면 404, `delta = 새 수량 − 기존 수량`으로 게이트 `reserve(delta, expectedUnitPrice, now)`(`delta = 0`도 상태·마감·가격 확인을 위해 호출), 참여 행 `quantity` 갱신, 완료되면 `GroupBuyCompleted` 발행.
- [ ] T025 [US4] `main/groupbuy/join/ParticipationController.java`에 `PUT /api/groupbuys/{id}/participations/me`(`@Valid`, 200) 추가.

**Checkpoint**: 네 스토리가 모두 통과한다.

---

## Phase 7: 측정 (헌법 II, 직접 풀기)

**Purpose**: 4방식을 같은 시나리오로 비교하고 결과를 문서로 남긴다.

- [ ] T026 [P] `load/seed.sql`과 `load/k6/last-seat.js`(남은 수량 1, 서로 다른 100명이 동시에 1개씩)를 작성한다.
- [ ] T027 [P] `load/k6/mixed.js`(60초 혼합 부하: 참여·수량 변경·참여 취소와 개설자 수정)와 남은 수량 10에 50명이 1~5개를 무작위로 요청하는 시나리오(S2)를 작성한다.
- [ ] T028 불변식 검증 쿼리(data-model.md의 I1 위반 쿼리와 초과 건수)를 시나리오 직후 실행하는 스크립트를 만든다.
- [ ] T029 방식 4개(`none`, `pessimistic`, `optimistic`, `conditional`)를 같은 환경(Docker MySQL 8.4, 커넥션 풀 20, JVM 힙 고정)에서 시나리오당 30회 반복 측정한다. 지표: 성공·거부 수(사유별), 초과 건수, I1 위반 건수, 모집 완료 전환 횟수, p50/p95/p99, 처리량, 낙관적 재시도 수, 오류율.
- [ ] T030 결과·환경·해석을 `docs/benchmarks/participation-concurrency.md`에 정리한다. 최종 운영 방식은 결과를 보고 사용자가 정하고 이유를 같은 문서에 적는다(기본 방식은 그때까지 `pessimistic`).

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T031 quickstart.md 시나리오 1~20을 모두 실행해 기대 결과와 대조한다.
- [ ] T032 [P] `docs/spikes.md`와 research.md 끝의 004 미검증 가정(REPEATABLE READ에서 `none`의 갱신 손실, 풀 크기 20이 병목인지, 낙관적과 직접 `UPDATE`의 버전 규칙)이 T014·T029에서 확인되었는지 표시한다.
- [ ] T033 [P] `GroupBuyCompleted` 발행 코드가 001 수정(T031)과 여기(T012, T024)에 중복되지 않게 공통 도우미로 모은다(`completeIfFull()` 결과로 수신자 id를 모으고 발행).
- [ ] T034 [P] 헌법 IV 필수 대상(수량 초과 판정, 모집 완료 전환, 동시 참여 경쟁)이 통합 테스트로 존재하고 통과하는지 확인한다.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1·2**: 001 구현 이후. T001·T002·T003·T005·T006은 병렬 가능, T004는 T003 이후, T007은 T004 이후, T008은 T007 이후, T009는 T007 이후.
- **US1 → US2 → US3 → US4** 순서로 진행한다. `ParticipationService`·`ParticipationController`를 공유하므로 병렬로 진행하지 않는다. US3·US4는 US1의 `join` 뼈대와 게이트를 쓴다.
- **측정(Phase 7)**: US2의 4방식 구현이 끝난 뒤. 풀 크기·힙 같은 환경은 고정해서 기록한다.
- **Polish**: 원하는 스토리가 끝난 뒤.

### 태스크 간 의존

- T012는 T007(게이트), T008(서비스 뼈대), T006(리포지토리) 이후. T013은 T012 이후.
- T016~T018은 서로 독립(다른 파일)이지만 T019가 모두 연결한다. T016은 `useAffectedRows=true`(001 T003·T004)에 의존한다.
- T014·T015는 구현 전에 작성하고 3방식 모두에서 실패를 확인한다(구현 전이라 실패해야 한다).

## Parallel Example

```bash
# Phase 1의 서로 다른 파일
T001 요청 / T002 응답 / T003 GateResult / T005 테스트 시드 / T006 리포지토리 메서드

# US1 테스트 두 개
T010 JoinParticipationIT / T011 IdempotentJoinIT

# US2 테스트 두 개와 방식 구현체 세 개
T014 QuantityRaceIT / T015 ParticipationSerializationIT
T016 Conditional / T017 Optimistic / T018 NoLock

# 측정 스크립트
T026 last-seat.js / T027 mixed.js
```

## Implementation Strategy

1. Phase 1·2 → US1(비관적 게이트만)로 멈추고 검증 → US2(나머지 3방식과 경쟁 테스트) → US3 → US4.
2. 측정은 정확성 테스트가 3방식에서 통과한 뒤에 한다. `none`은 일부러 틀리게 만든 구현이라 정확성 테스트에 넣지 않는다.
3. 시간 상한(헌법 I)이 걸리면 mixed 부하(T027)와 Polish를 줄이되 마지막 자리 시나리오(T026, T029)와 결과 문서(T030)는 줄이지 않는다.

## Notes

- **(핵심 로직)** 태스크(T007, T012, T016~T018, T021, T024)는 직접 쓴다. 이 기능의 학습 목표(4방식 비교)가 거기에 있다.
- 참여·수량 변경·참여 취소 자체는 알림 이벤트를 발행하지 않는다(spec FR-012). 모집 완료만 발행한다.
- 각 태스크 또는 논리적 묶음 후 커밋한다. 측정 결과 커밋에는 환경과 해시를 함께 적는다.
- 체크포인트마다 멈춰서 스토리 단독 동작을 검증한다.
