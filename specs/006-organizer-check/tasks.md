---

description: "개설자용 참여자·입금·수령 체크 구현 태스크"
---

# Tasks: 개설자용 참여자·입금·수령 체크

**Input**: `/specs/006-organizer-check/` 의 plan.md, spec.md, research.md, data-model.md, contracts/check-api.md, quickstart.md

**Prerequisites**: plan.md, spec.md. **001·002·003·004 구현이 먼저 끝나 있어야 한다**: `GroupBuy`, `GroupBuyStatus`(`COMPLETED`), `Participation`/`ParticipationRepository`, `ApiException`/`ApiExceptionHandler`, `IntegrationTestBase`(`MutableClock`, `as(memberId)`), 002의 로그인 세션, 003의 상세(`GroupBuyDetail.myParticipation`, 개설자용 명단 쿼리, `QueryCountInspector`). 모집 완료 공구는 004의 마지막 수량 참여로 만들거나 SQL 시드로 만든다. 005는 선행 조건이 아니다.

**Tests**: 이 기능에는 헌법 IV 필수 대상이 없다. 접근 규칙, 멱등, 동시 체크, 쿼리 수 상한은 Testcontainers(MySQL) 통합 테스트로 덮는다. 각 스토리에서 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.

**Organization**: 사용자 스토리별로 묶었다. 이 프로젝트는 Spec Kit의 `implement` 명령은 쓰지 않는다. 구현은 작업 세션(AI)이 하고, **일반 태스크는 Phase 끝에 묶음 승인, `(핵심 로직)` 태스크는 태스크마다 승인**을 받는다(2026-10-27 개정, `docs/sessions/README.md`). **(핵심 로직)** 표시 태스크는 승인 요약에 설명 체크 질문이 붙는다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 태스크에 의존하지 않아 병렬 가능
- **[Story]**: US1(입금 체크), US2(수령 체크), US3(참여자 본인 상태)
- 경로의 `main/`은 `src/main/java/com/camgong/`, `test/`는 `src/test/java/com/camgong/`, `res/`는 `src/main/resources/` 를 뜻한다. 이 기능의 새 코드는 하위 패키지 `groupbuy/check/`에 둔다. 애그리거트 수정은 `groupbuy/`에 한다.

## Phase 1: Setup

**Purpose**: 컬럼, 응답 모양, 오류 코드, 테스트 시드

- [ ] T001 `res/schema.sql`의 `participation` 정의에 `paid_at DATETIME(6) NULL`, `received_at DATETIME(6) NULL` 추가(data-model.md). `schema.sql`은 `CREATE TABLE IF NOT EXISTS`라서 이미 만들어진 DB에는 컬럼이 안 생기므로, 운영 데이터가 생기기 전에는 001의 `participation` 정의 안에 직접 넣는다. 개발 DB는 볼륨을 지우고 다시 만든다(지우는 일은 사용자 승인). `main/groupbuy/Participation.java`에 `Instant paidAt`, `Instant receivedAt` 필드를 `@Column(insertable = false, updatable = false)`로 추가한다 — 쓰기는 T005의 `UPDATE`만 하고 Hibernate가 이 컬럼을 덮어쓰지 않게 한다. 주석: `// ponytail: 확인 시각은 조건부 UPDATE로만 쓴다(research 결정 2)`
- [ ] T002 [P] `main/groupbuy/check/` 응답 record 작성(contracts/check-api.md): `CheckRequest(Boolean confirmed)`(`@NotNull`), `CheckRow(memberId, name, quantity, amount, paidConfirmedAt, receivedConfirmedAt, receivedWithoutPayment)`, `CheckSummary(participantCount, paidCount, receivedCount, amountDue, amountConfirmed)`, `CheckSheet(summary, rows)`. 시각은 오프셋 포함 ISO-8601(001과 같은 직렬화 설정, KST 고정).
- [ ] T003 [P] 오류 코드 정의: `docs/architecture.md` 6.1 오류 코드 표의 이름을 그대로 쓴다 — 403 `ORGANIZER_ONLY`, 409 `NOT_CHECKABLE`, 개설자 본인 지정은 400 `INVALID_TARGET`(`errors`에 `memberId`, research 결정 7). 001의 `ApiException` 사용 방식을 따른다. 표에 이름이 없거나 다르면 임의로 정하지 말고 멈춰서 알린다.
- [ ] T004 [P] `test/support/IntegrationTestBase.java`에 시드 헬퍼 추가: quickstart 준비 상태(개설자 수량 9 + 참여자 2가 6개 + 참여자 3이 9개로 `COMPLETED`, 총 24, 개당 1,200원)를 만드는 메서드, 상태를 지정해 공구를 만드는 메서드(`OPEN`/`FAILED`/`CANCELLED`/마감됨), 참여자 없이 `COMPLETED`인 공구를 SQL로 만드는 메서드(시나리오 11), 참여자 N명을 넣는 메서드(시나리오 18).

---

## Phase 2: Foundational (모든 스토리의 선행 조건)

**Purpose**: 접근 규칙, 체크표 쿼리, 조건부 UPDATE

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 스토리 작업을 시작하지 않는다.

- [ ] T005 `main/groupbuy/ParticipationRepository.java`에 쿼리 추가 **(핵심 로직)**:
  - 체크표 목록: 003의 개설자용 명단 쿼리를 재사용해 `paid_at`, `received_at`을 더하고 `p.member_id <> :organizerId`로 개설자를 제외, `ORDER BY p.id`. 003이 N+1을 푼 최종 방식(fetch join / batch / 프로젝션)을 **그대로** 쓴다(research 결정 4). `filter=unpaid`는 `paid_at IS NULL`, `unreceived`는 `received_at IS NULL`.
  - 요약: `COUNT(*)`, `COUNT(paid_at)`, `COUNT(received_at)`, `SUM(quantity)`, `SUM(CASE WHEN paid_at IS NOT NULL THEN quantity END)`를 개설자 제외 전체 기준으로(필터와 무관).
  - 입금/수령 지정과 되돌리기 네 개: `SET paid_at = COALESCE(paid_at, :now)` / `SET paid_at = NULL`(수령도 같은 모양), 조건은 `group_buy_id`, `member_id`, `member_id <> (SELECT organizer_id FROM group_buy WHERE id = :groupBuyId)`. 입금과 수령은 **별도 UPDATE**여서 서로 영향을 주지 않는다.
  - 참여 행 존재 확인: 공구+회원으로 1건.
  - 이 쿼리들에는 락 읽기가 필요 없다. `COMPLETED`는 종료 상태라 참여 행과 공구 행이 이후 바뀌지 않고 락 대기도 없어서 REPEATABLE READ 스냅샷이 낡을 일이 없다(004·005의 잠금 읽기 규칙과 다른 점). 이 이유를 주석으로 남긴다.
- [ ] T006 `main/groupbuy/check/ParticipationCheckService.java`의 접근 검사 `requireCheckable(groupBuyId, requesterId)` **(핵심 로직)**: 순서는 공구 없음 또는 `CANCELLED` → 404, 요청자가 개설자가 아니면 403 `ORGANIZER_ONLY`, 상태가 `COMPLETED`가 아니면 409 `NOT_CHECKABLE`. 개설자가 아닌 사람이 공구 상태를 알아내지 못하게 403을 409보다 먼저 판정한다. 비로그인 401은 002의 보호 필터가 처리한다. `NOT_CHECKABLE`에는 현재 상태를 `reason`으로 싣지 않는다(오류 코드 표에 reason 정의가 없다).

**Checkpoint**: `requireCheckable`과 T005 쿼리를 서비스 테스트로 직접 호출해 `COMPLETED` 공구에서만 통과한다.

---

## Phase 3: User Story 1 - 참여자별 입금을 체크하기 (Priority: P1) 🎯 MVP

**Goal**: 개설자가 체크표를 열어 이름·수량·금액·요약을 보고, 입금을 체크하고 되돌린다.

**Independent Test**: 모집이 완료된 공구의 개설자가 체크표를 열어 참여자 2명의 내용을 확인하고 입금을 체크했다가 되돌려 요약이 맞게 바뀌는지, 개설자가 아닌 사람은 못 보는지 확인한다.

### Tests for User Story 1 ⚠️ (먼저 작성하고 실패 확인)

- [ ] T007 [P] [US1] `test/groupbuy/check/CheckSheetIT.java`: 시나리오 1(참여자 2명의 이름·수량·금액 7,200 / 10,800, `summary` 입금 0/2·수령 0/2·받아야 할 금액 18,000·확인된 금액 0, 개설자 행 없음), 11(참여자 없이 `COMPLETED` → `rows: []`, `participantCount: 0`), 14(`filter=unpaid`/`unreceived`는 `rows`만 거르고 `summary`는 전체 기준 그대로), 15(`filter=x` → 400), 17(체크 후 다시 열기 같은 상태)
- [ ] T008 [P] [US1] `test/groupbuy/check/CheckAccessIT.java`: 시나리오 4(참여자·외부인이 체크표·PUT → 403 `ORGANIZER_ONLY`), 5(`OPEN`·마감됨·`FAILED` 공구 → 409 `NOT_CHECKABLE`, `CANCELLED` → 404), 12(`memberId`=개설자 → 400, `errors`에 `memberId`), 13(이 공구에 참여하지 않은 `memberId` → 404), 15(본문 `{}` → 400), 비로그인 401. 개설자가 아닌 사용자가 `OPEN` 공구에서 403을 받는지(상태를 알아내지 못함)도 확인한다.
- [ ] T009 [P] [US1] `test/groupbuy/check/CheckConfirmIT.java`의 입금 부분: 시나리오 2(`payment` `confirmed=true` → 200, `paidConfirmedAt` 기록, 요약 입금 1/2·확인 금액 7,200), 3(`false` → 시각 null, 요약 복귀), 6(같은 `true`를 두 번 → 결과 동일, 두 번째 호출 뒤 확인 시각이 첫 호출 시각 그대로 — `MutableClock`을 움직여 확인), 19(체크 요청에 이벤트가 발행되지 않음 — `EventCollector`가 비어 있음)

### Implementation for User Story 1

- [ ] T010 [US1] `ParticipationCheckService.java`에 `getCheckSheet(groupBuyId, requesterId, filter)`: `requireCheckable` 후 T005의 목록과 요약을 읽어 `CheckSheet`을 만든다. `amount = unitPrice × quantity`(`long` 곱셈, 개당 가격은 공구에서 읽는다), `amountDue`는 개설자 제외 전체 금액 합, `amountConfirmed`는 입금 확인된 참여자의 금액 합, `receivedWithoutPayment = paidAt == null && receivedAt != null`. 지원하지 않는 `filter`는 400.
- [ ] T011 [US1] `ParticipationCheckService.setPayment(groupBuyId, requesterId, memberId, confirmed)` **(핵심 로직)**: `requireCheckable` → `memberId`가 개설자면 400 `INVALID_TARGET` → 참여 행이 없으면 404 → `confirmed`에 따라 지정/되돌리기 UPDATE 하나 실행(같은 트랜잭션) → 갱신된 행을 `CheckRow`로 돌려준다. 영향 행 0은 "이미 그 상태"일 수 있어(`useAffectedRows=true`) 영향 행 수로 오류를 만들지 않는다. 이벤트를 발행하지 않는다(FR-008).
- [ ] T012 [US1] `main/groupbuy/check/ParticipationCheckController.java`: `GET /api/groupbuys/{id}/check-sheet?filter=`, `PUT /api/groupbuys/{id}/participations/{memberId}/payment`. 요청자는 002의 인증 결과에서 얻는다(001의 컨트롤러가 쓰는 방식 그대로). 오류 본문은 001의 `ProblemDetail` + `errors[]`.

**Checkpoint**: US1이 단독으로 동작한다. T007~T009가 통과한다.

---

## Phase 4: User Story 2 - 참여자별 수령을 체크하기 (Priority: P2)

**Goal**: 수령을 입금과 독립적으로 체크하고 되돌린다.

**Independent Test**: 입금 여부와 상관없이 수령을 체크하고 되돌려 요약과 상태가 맞게 바뀌는지 확인한다.

### Tests for User Story 2 ⚠️

- [ ] T013 [P] [US2] `test/groupbuy/check/CheckConfirmIT.java`에 수령 부분 추가: 시나리오 7(입금 미확인 참여자에게 `receipt` `true` → 200, 수령 시각 기록, 요약 수령 1/2, 입금 그대로, 행의 `receivedWithoutPayment=true`), 8(`receipt` `false` → 수령만 확인 전, 입금 불변), 9(두 참여자의 입금·수령을 모두 체크 → 요약 입금 2/2·수령 2/2·확인 금액 = 받아야 할 금액), 입금 되돌리기가 수령 시각에 영향 없음

### Implementation for User Story 2

- [ ] T014 [US2] `ParticipationCheckService.setReceipt(...)`: T011과 같은 흐름에서 컬럼만 `received_at`. 코드가 겹치면 컬럼 이름을 인자로 받지 말고(SQL 문자열 조립 금지) 입금/수령 UPDATE 메서드를 각각 호출한다. `ParticipationCheckController`에 `PUT …/participations/{memberId}/receipt` 추가.

**Checkpoint**: T013이 통과한다.

---

## Phase 5: User Story 3 - 참여자가 내 입금·수령 확인 상태 보기 (Priority: P2)

**Goal**: 참여자가 공구 상세에서 자기 입금·수령 확인 상태만 본다.

**Independent Test**: 개설자가 한 참여자의 입금을 체크한 뒤, 그 참여자와 다른 참여자가 각자 상세를 열어 자기 상태만 보이는지 확인한다.

### Tests for User Story 3 ⚠️

- [ ] T015 [P] [US3] `test/groupbuy/check/MyConfirmStatusIT.java`: 시나리오 10(입금이 체크된 참여자 2는 `paidConfirmedAt` 값·`receivedConfirmedAt` null, 미체크 참여자 3은 둘 다 null, 서로의 상태는 응답 어디에도 없음), 개설자의 `myParticipation`에는 두 필드가 없음, 체크를 되돌린 뒤 다시 불러오면 null(SC-006)

### Implementation for User Story 3

- [ ] T016 [US3] `main/groupbuy/query/GroupBuyDetail.java`의 `myParticipation`에 `paidConfirmedAt`, `receivedConfirmedAt`(nullable) 추가. 값은 본인의 `participation` 행에서 읽고, 개설자일 때는 이 필드를 응답에서 뺀다(`@JsonInclude(NON_NULL)`로는 `null`인 참여자 값과 구분되지 않으니 개설자용과 참여자용 모양을 분리하거나, 003의 응답 구성 방식에 맞춰 개설자에게는 `myParticipation` 안에 필드를 넣지 않는다). 003의 `detail`이 이미 읽는 본인 참여 행을 쓰므로 쿼리를 늘리지 않는다. 003 `QueryCountIT`의 상한이 유지되는지 확인한다(미검증 가정 2).

**Checkpoint**: T015가 통과하고 003의 기존 테스트가 깨지지 않는다.

---

## Phase 6: 동시성과 규모 (횡단 요구: FR-005, SC-002, SC-007)

- [ ] T017 [P] `test/groupbuy/check/CheckConcurrencyIT.java`: 시나리오 16(같은 참여자에 `true`/`false`를 스레드 여러 개가 각자 커넥션으로 동시에 → 최종 상태가 마지막으로 처리된 요청과 같고 `paid_at`이 NULL이거나 시각이 정확히 하나, 오류 응답 없음. CI 100회 반복), 같은 `true`를 동시에 여러 번 → 확인 시각이 하나로 유지됨
- [ ] T018 [P] `test/groupbuy/query/QueryCountIT.java` 또는 `test/groupbuy/check/CheckSheetQueryCountIT.java`: 시나리오 18(참여자 1, 20, 50명 체크표의 SQL 수가 같고 상한 이하 — 상한은 003 개설자 상세와 같은 방식으로 정한다). 쿼리 수가 참여자 수에 비례하면 003의 최종 명단 구현을 쓰고 있는지 T005를 점검한다.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T019 quickstart.md 시나리오 1~19를 모두 실행해 기대 결과와 대조한다(18의 응답 시간은 T020).
- [ ] T020 참여자 50명 공구의 체크표 요청 응답 시간(95% 1초 이내, SC-002)을 측정해 환경과 함께 `docs/benchmarks/check-sheet.md`에 기록한다. 헌법 II 측정 대상은 아니므로 기록용이다.
- [ ] T021 [P] research.md 끝의 006 미검증 가정(`COALESCE` 갱신이 이미 지정된 행에 영향 행 0을 돌려줘도 멱등 판정에 영향이 없다, 명단 쿼리에 컬럼 2개를 더해도 쿼리 수 상한이 유지된다)이 T011·T016·T018에서 확인되었는지 표시한다.
- [ ] T022 [P] `GroupBuyDetail`/`ParticipantView`에 003의 T035 주석(006이 같은 쿼리를 쓴다)이 남아 있고 이 기능의 변경과 맞는지 확인한다.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1·2**: 001~004 구현 이후. T002·T003·T004는 병렬 가능. T005는 T001 이후, T006은 T003 이후.
- **US1 → US2 → US3** 순서. US2는 US1의 서비스·컨트롤러 위에 쌓는다. US3은 003의 상세와 T001의 컬럼만 있으면 되어 US1·US2와 독립이다(US1 체크가 있어야 값이 보이는 시험을 할 수 있으므로 US1 이후에 진행한다).
- **Phase 6·7**: 원하는 스토리가 끝난 뒤.

### 태스크 간 의존

- T010·T011은 T005·T006 이후. T012는 T010·T011 이후. T014는 T011 이후. T016은 T001 이후이며 003의 `detail`이 있어야 한다.
- T017·T018은 T012·T014 이후에 실행하지만 구현 전에 작성해 실패를 확인한다.

## Parallel Example

```bash
# Phase 1의 서로 다른 파일
T002 응답 record / T003 오류 코드 / T004 테스트 시드

# 스토리별 테스트
T007 CheckSheetIT / T008 CheckAccessIT / T009 CheckConfirmIT(입금) / T015 MyConfirmStatusIT
```

## Implementation Strategy

1. Phase 1·2 → US1로 멈추고 검증(MVP: 개설자가 입금을 체크한다) → US2 → US3 → 동시성·규모.
2. 줄여야 하면 US3(참여자 본인 상태) → US2(수령) 순서로 줄인다. 체크표와 입금 체크(US1)는 이 기능의 존재 이유라 남긴다.

## Notes

- **(핵심 로직)** 태스크(T005, T006, T011)는 조건부 UPDATE·접근 규칙·멱등을 다룬다. 구현은 세션이 하고 승인 요약에 설명 체크 질문이 붙는다(research 결정 1·2: 토글이 아니라 목표 상태 지정이 더블 클릭에서 안전한 이유, 공구 행 락이 필요 없는 이유).
- 003 `contracts/browse-api.md`의 `myParticipation`에는 `paidConfirmedAt`, `receivedConfirmedAt`이 이미 반영되어 있다(2026-10-27). T016은 그 계약을 따른다.
- 체크는 알림을 만들지 않는다(FR-008). 이벤트를 발행하는 코드를 넣지 않는다.
- 각 태스크 또는 논리적 묶음 후 커밋한다.
