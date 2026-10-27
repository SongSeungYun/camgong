---

description: "공구 목록·상세 조회 구현 태스크"
---

# Tasks: 공구 목록·상세 조회

**Input**: `/specs/003-browse-groupbuy/` 의 plan.md, spec.md, research.md, data-model.md, contracts/browse-api.md, quickstart.md

**Prerequisites**: plan.md, spec.md. **001 구현(특히 US1)이 먼저 끝나 있어야 한다**: `GroupBuy`, `Participation`, 리포지토리, `ApiException`/`ApiExceptionHandler`, `IntegrationTestBase`(MySQL Testcontainers, `MutableClock`), 임시 인증(`X-Member-Id`). 004·005·002는 필요 없다. 상태별 공구와 참여자는 테스트에서 SQL이나 리포지토리로 시드한다.

**Tests**: 헌법 II가 N+1 전후 쿼리 수 기록을 요구하고, 이 기능의 규칙(공개 범위, 키셋 정합성, 검색)은 통합 테스트로 증명한다. 각 스토리에서 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.

**Organization**: 사용자 스토리별로 묶었다. 이 프로젝트는 Spec Kit의 `implement` 명령은 쓰지 않는다. 구현은 작업 세션(AI)이 태스크 단위로 하고, 사용자가 승인한 뒤에 커밋한다(2026-10-27 변경, `docs/sessions/README.md`). **(핵심 로직)** 표시 태스크는 승인 요약에 설명 체크 질문이 붙는다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 태스크에 의존하지 않아 병렬 가능
- **[Story]**: US1(목록), US2(상세), US3(내 활동)
- 경로의 `main/`은 `src/main/java/com/camgong/`, `test/`는 `src/test/java/com/camgong/`, `res/`는 `src/main/resources/` 를 뜻한다. 이 기능의 새 코드는 하위 패키지 `groupbuy/query/`에 둔다.

## Phase 1: Setup

**Purpose**: 쿼리 수 측정 도구와 인덱스, 연관관계 매핑

- [ ] T001 `test/support/QueryCountInspector.java` 작성: Hibernate `StatementInspector`로 요청(스레드) 단위로 실행된 SQL 개수를 센다(`reset()`, `count()` 제공). 테스트 프로파일에서만 켜는 설정 `spring.jpa.properties.hibernate.session_factory.statement_inspector=com.camgong.support.QueryCountInspector`를 `src/test/resources/application-test.yml`에 추가한다. JPA/Hibernate를 거친 쿼리만 센다(`JdbcClient` 쿼리는 세지 않음 — 조회 경로는 JPA만 쓴다).
- [ ] T002 `res/schema.sql`에 인덱스 2개 추가: `participation (member_id, group_buy_id)`, `group_buy (organizer_id, id)`. `schema.sql`은 앱을 시작할 때마다 다시 실행되고 MySQL은 `CREATE INDEX IF NOT EXISTS`를 지원하지 않는 것으로 알고 있으므로(구현 시 확인), 별도 `CREATE INDEX` 문이 아니라 각 테이블의 `CREATE TABLE IF NOT EXISTS` 정의 안에 `KEY` 절로 넣는다. 005가 추가하는 `group_buy (status, deadline)`도 같은 방식이며 이 태스크 대상이 아니다.
- [ ] T003 [P] `main/groupbuy/GroupBuy.java`에 읽기 전용 연관관계 추가: `@ManyToOne(fetch = LAZY) @JoinColumn(name = "organizer_id", insertable = false, updatable = false) Member organizer`. 기존 `Long organizerId` 필드는 그대로 둔다. 쓰기 경로(001, 004, 005)에서는 이 필드에 접근하지 않는다.
- [ ] T004 [P] `main/groupbuy/Participation.java`에 읽기 전용 연관관계 추가: `groupBuy`(`@JoinColumn(name = "group_buy_id", insertable = false, updatable = false)`), `member`(`@JoinColumn(name = "member_id", insertable = false, updatable = false)`), 둘 다 `@ManyToOne(fetch = LAZY)`. 기존 `Long groupBuyId`, `Long memberId` 필드는 그대로 둔다.

---

## Phase 2: Foundational (모든 스토리의 선행 조건)

**Purpose**: 표시 상태, 커서, 요청 파라미터 — 세 스토리가 모두 쓴다.

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 스토리 작업을 시작하지 않는다.

- [ ] T005 [P] `main/groupbuy/query/DisplayStatus.java` **(핵심 로직)**: enum `OPEN`, `PENDING_CLOSE`, `COMPLETED`, `FAILED`와 `derive(status, deadline, now)`. 규칙: `OPEN` = `status='OPEN' AND deadline > now`, `PENDING_CLOSE` = `status='OPEN' AND deadline <= now`, `COMPLETED` = `status='COMPLETED'`, `FAILED` = `status='FAILED'`. `CANCELLED`는 어디에도 노출하지 않는다(`derive`에 넘어오면 예외). 쿼리 필터용으로 같은 조건을 Criteria 조건으로도 만드는 메서드를 함께 둔다(T013이 쓴다).
- [ ] T006 [P] `main/groupbuy/query/PageCursor.java` **(핵심 로직)**: 불투명 커서 문자열(JSON → Base64URL) 인코딩/디코딩. 담는 값은 `sort`, 최신순은 `id`, 마감 임박순은 `(deadline, id)`. 잘못된 문자열이거나 요청의 `sort`와 맞지 않으면 400(`errors`에 `cursor`).
- [ ] T007 [P] `main/groupbuy/query/GroupBuyListQuery.java`: 요청 파라미터 record. `status`(`OPEN`/`PENDING_CLOSE`/`COMPLETED`/`FAILED`, 기본 `OPEN`), `category`(6개 중 하나, 선택), `q`(앞뒤 공백 제거, 50자 이하, 공백으로 나눈 단어 최대 5개, 비어 있으면 검색 안 함), `sort`(`latest`/`deadline`, 기본 `latest`), `cursor`, `size`(1~50, 기본 20). 지원하지 않는 값은 400(`errors`에 해당 파라미터 이름).

**Checkpoint**: 세 클래스의 단위 동작이 확인된다(T005~T007).

---

## Phase 3: User Story 1 - 공구 목록 보기 (Priority: P1) 🎯 MVP

**Goal**: 비로그인도 모집 중 공구를 카드로 보고, 정렬·필터·검색·'더 보기'를 쓴다.

**Independent Test**: 상태가 다른 공구를 준비해 로그인 없이 목록을 열고 노출 대상, 표시 내용, 정렬, 필터, 검색, 페이지 넘김이 규칙대로인지 확인한다.

### Tests for User Story 1 ⚠️ (먼저 작성하고 실패 확인)

- [ ] T008 [P] [US1] `test/groupbuy/query/ListGroupBuyIT.java`: quickstart 시나리오 1(비로그인 목록, 카드 필드 전부), 2(취소 제외), 3(`status=PENDING_CLOSE`는 마감 지난 `OPEN`, 기본 목록에는 없음), 4(`sort=deadline`: 마감 이른 순, 같은 마감은 id 순, 어떤 `status` 필터에서도 같은 규칙), 6(`category=FOOD`), 7(`q=햇`, `q=햇반`, `q=24 햇반`, `q=24 라면`), 8(`q=%`, `q=_`은 문자 그대로), 9(잘못된 `sort`·`size`·`q` 51자·커서 → 400), 10(필터 조합)
- [ ] T009 [P] [US1] `test/groupbuy/query/PaginationConsistencyIT.java`: 시나리오 5(공구 45건을 `size=20`으로 `nextCursor`를 따라 끝까지 → 45건이 한 번씩, 마지막 `nextCursor=null`), 11(1페이지를 본 뒤 새 공구 개설·상태 변화, 이어서 2페이지 → 이미 본 공구가 다시 나오지 않음), 마감 시각이 같은 공구가 여러 개일 때 마감 임박순 넘김에 중복·누락 없음

### Implementation for User Story 1

- [ ] T010 [P] [US1] `main/groupbuy/query/GroupBuySummary.java`: 목록 카드 record — `id`, `title`, `itemName`, `category`, `unitPrice`, `securedQuantity`, `totalQuantity`, `deadline`(`OffsetDateTime`), `status`(표시 상태). 개설자 정보와 구매처 링크는 넣지 않는다.
- [ ] T011 [US1] `main/groupbuy/query/GroupBuySearchRepository.java` **(핵심 로직)**: JPA Criteria API로 동적 조건 조립 — 표시 상태 조건(T005), `category`, 검색 단어마다 `(title LIKE :w ESCAPE '\' OR item_name LIKE :w ESCAPE '\')`를 `AND`로 연결(`:w`는 `%` + 이스케이프한 단어 + `%`, `%`·`_`·`\` 이스케이프), 키셋 조건(최신순 `id < :cursorId`, 마감 임박순 `(deadline > :d OR (deadline = :d AND id > :id))`), 정렬(최신순 `id DESC`, 마감 임박순 `deadline ASC, id ASC`), `setMaxResults(size + 1)`. 카운트 쿼리는 하지 않는다. 항상 `status <> 'CANCELLED'`. 카드 컬럼만 DTO 프로젝션으로 읽는다.
- [ ] T012 [US1] `main/groupbuy/query/GroupBuyQueryService.java`의 `list` **(핵심 로직)**: `size + 1` 건 중 `size`건을 돌려주고 초과분이 있으면 `PageCursor`로 `nextCursor`를 만든다(없으면 `null`). 표시 상태는 `Instant.now(clock)` 기준으로 `DisplayStatus.derive`를 적용한다. 읽기 전용 트랜잭션.
- [ ] T013 [US1] `main/groupbuy/query/GroupBuyQueryController.java`: `GET /api/groupbuys`(인증 불필요 — `X-Member-Id`가 있어도 쓰지 않는다), 응답 `{ items: [...], nextCursor }`. 파라미터 검증 실패는 `ApiExceptionHandler`를 통해 400 + `errors[]`.

**Checkpoint**: US1이 단독으로 동작한다. T008·T009가 통과한다.

---

## Phase 4: User Story 2 - 공구 상세 보기 (Priority: P2)

**Goal**: 로그인한 회원이 상세를 보고, 역할별 공개 규칙(오픈채팅 링크, 명단)이 지켜진다.

**Independent Test**: 개설자, 참여자, 참여하지 않은 회원, 비로그인으로 같은 공구 상세를 열어 보이는 정보가 규칙과 같은지 확인한다.

### Tests for User Story 2 ⚠️

- [ ] T014 [P] [US2] `test/groupbuy/query/GroupBuyDetailIT.java`: 시나리오 12(일반 회원: 개설자 이름·구매처 링크·수량·마감·상태·`participantCount`, `participants=null`, `myParticipation=null`, `openChatUrl=null`), 13(내 수량 6·개당 1,200 → `myParticipation={quantity:6, amount:7200}`), 14(개설자 상세: `participants`에 개설자 포함 이름·수량, `isOrganizer=true`), 15(`openChatUrl` 공개 표: 개설자는 `OPEN`·`PENDING_CLOSE`·`COMPLETED`에서 값(data-model 우선, FAILED 제외), 참여자는 `COMPLETED`에서만, 외부인·`FAILED`·`PENDING_CLOSE`의 참여자는 `null`), 16(헤더 없음 401), 17(없는 id·취소된 id 404)
- [ ] T015 [P] [US2] `test/groupbuy/query/QueryCountIT.java`의 시나리오 B(상세의 개설자용 명단): 참여자 N = 1, 20, 50명으로 상세를 불러 `QueryCountInspector`로 쿼리 수를 기록하는 테스트를 먼저 작성한다. 이 시점에는 상한 단언 없이 **측정값만 출력**한다(순진한 구현의 기준선을 남기기 위해).

### Implementation for User Story 2

- [ ] T016 [P] [US2] `main/groupbuy/query/GroupBuyDetail.java`, `main/groupbuy/query/ParticipantView.java`: contracts/browse-api.md의 상세 응답 필드 그대로 — `id`, `title`, `description`, `itemName`, `category`, `itemUrl`, `unitPrice`, `totalQuantity`, `securedQuantity`, `remainingQuantity`, `deadline`, `status`(표시 상태), `organizerName`, `isOrganizer`, `participantCount`(개설자 포함), `myParticipation{quantity, amount}`|null, `openChatUrl`|null, `participants[{memberId, name, quantity}]`|null, `createdAt`.
- [ ] T017 [US2] `main/groupbuy/ParticipationRepository.java`에 조회 메서드 추가: 공구+회원으로 내 참여 1건, 공구별 참여자 수, 공구별 참여 목록(명단용). **이 시점은 순진한 구현**이다: 명단은 `participation` 목록을 읽고 서비스에서 `participation.getMember().getName()`을 불러 지연 로딩이 일어나게 둔다.
- [ ] T018 [US2] `main/groupbuy/query/GroupBuyQueryService.java`의 `detail` **(핵심 로직)**: 회원 확인(없으면 401), 공구가 없거나 `CANCELLED`면 404, 개설자 이름, 내 참여와 `amount = unitPrice × quantity`, `participantCount`, 오픈채팅 링크 공개 규칙(data-model.md 표: 개설자는 `OPEN`·`COMPLETED`, 참여자는 `COMPLETED`만, 그 외 `null`), 명단은 **개설자일 때만** 조회하고 그 외에는 `null`. 표시 상태는 `DisplayStatus.derive`.
- [ ] T019 [US2] `main/groupbuy/query/GroupBuyQueryController.java`에 `GET /api/groupbuys/{id}` 추가(로그인 필수). 001의 최소 상세(`GroupBuyService.get`, `GroupBuyController`의 `GET`, `GroupBuyResponse`의 상세 용도)를 **제거하고 이 구현으로 대체**한다. 001의 `LinkVisibilityIT`(T025)는 이 상세를 대상으로 하도록 옮기거나 `GroupBuyDetailIT`와 합친다.
- [ ] T020 [US2] **순진한 구현 시점 기록**: T015 측정을 실행해 N = 1, 20, 50의 쿼리 수를 `docs/benchmarks/browse-n-plus-one.md`의 시나리오 B ① 열에 적고 커밋한다(`docs: baseline N+1 for detail roster`). 커밋 해시를 문서에 적는다.

**Checkpoint**: US2가 단독으로 동작한다. T014가 통과하고 시나리오 B 기준선이 기록되었다.

---

## Phase 5: User Story 3 - 내 활동 보기 (Priority: P3)

**Goal**: 내가 연 공구와 내가 참여한 공구를 따로 모아 본다.

**Independent Test**: 한 회원이 공구를 열고 다른 공구에 참여한 상태에서 두 목록이 맞게 나뉘는지, 취소된 공구가 빠지는지 확인한다.

### Tests for User Story 3 ⚠️

- [ ] T021 [P] [US3] `test/groupbuy/query/MyActivityIT.java`: 시나리오 18(2개 개설·3개 참여 → `role=organizer` 2건, `role=participant` 5건(개설한 2건 포함), 개설 시각 늦은 순, `status`), 19(참여한 공구를 개설자가 취소 → 두 목록에서 사라짐), 20(헤더 없음 401), `role` 누락·잘못된 값 400, 키셋 넘김
- [ ] T022 [P] [US3] `test/groupbuy/query/QueryCountIT.java`의 시나리오 A(내가 참여한 공구 한 페이지): 참여 공구 N = 1, 20, 50건에서 쿼리 수를 측정만 출력하는 테스트를 먼저 작성한다.

### Implementation for User Story 3

- [ ] T023 [P] [US3] `main/groupbuy/query/MyActivityItem.java`: 목록 카드와 같은 필드 + `role=participant`일 때 `myQuantity`.
- [ ] T024 [US3] `main/groupbuy/ParticipationRepository.java`와 `main/groupbuy/GroupBuyRepository.java`에 내 활동 조회 추가 **(핵심 로직)**: 내가 연 공구는 `organizer_id = :me AND status <> 'CANCELLED' [AND id < :cursor] ORDER BY id DESC LIMIT size+1`, 내가 참여한 공구는 `participation`을 거쳐 `g.id DESC` 키셋. **이 시점은 순진한 구현**이다: 참여 목록을 읽고 항목마다 `participation.getGroupBuy()`를 지연 로딩해 `1 + N` 쿼리가 생기게 둔다.
- [ ] T025 [US3] `main/groupbuy/query/GroupBuyQueryService.java`의 `mine`과 `main/groupbuy/query/GroupBuyQueryController.java`의 `GET /api/me/groupbuys?role=organizer|participant`(로그인 필수, `cursor`, `size`): `role` 필수, 정렬은 공구 개설 시각이 늦은 순 고정, 취소된 공구 제외, 목록과 같은 `nextCursor` 규칙.
- [ ] T026 [US3] **순진한 구현 시점 기록**: T022 측정을 실행해 N별 쿼리 수를 `docs/benchmarks/browse-n-plus-one.md`의 시나리오 A ① 열에 적고 커밋한다(`docs: baseline N+1 for my activity`).

**Checkpoint**: 세 스토리가 모두 통과한다. N+1 기준선 두 개가 커밋되어 있다.

---

## Phase 6: N+1 개선 (헌법 II, 직접 풀기)

**Purpose**: 시나리오 A(내가 참여한 공구), B(상세 명단)를 단계마다 커밋하고 쿼리 수를 기록한다. 코드에는 최종 구현만 남고 단계별 수치와 커밋 해시는 문서에 남는다.

- [ ] T027 [US2] [US3] ② **fetch join**: 시나리오 B는 `participation join fetch member`, 시나리오 A는 `participation join fetch groupBuy`로 바꾼다. N = 1, 20, 50의 쿼리 수를 측정해 `docs/benchmarks/browse-n-plus-one.md` ② 열에 적고 커밋한다.
- [ ] T028 [US2] [US3] ③ **batch size**: ②를 되돌린 상태(또는 fetch join 없이)에서 `hibernate.default_batch_fetch_size`로 `IN` 쿼리가 나가는지 확인하고 같은 방식으로 ③ 열에 기록·커밋한다. fetch join과의 차이(쿼리 수, 조인 중복)를 문서에 적는다.
- [ ] T029 [US2] [US3] ④ **DTO 프로젝션**: 필요한 컬럼만 조인으로 읽는 프로젝션으로 바꾸고 ④ 열에 기록·커밋한다.
- [ ] T030 [US2] [US3] 최종 구현 선택 후 `QueryCountIT`에 **상한 단언**을 건다: 시나리오 A는 N과 무관하게 **2 이하**, 시나리오 B는 N과 무관하게 **4 이하**(개설자 외 상세는 3 이하). 선택 이유와 각 단계의 해석을 `docs/benchmarks/browse-n-plus-one.md`에 정리한다.

**Checkpoint**: 두 시나리오의 쿼리 수가 N과 무관하고 단계별 기록이 문서에 있다.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T031 `load/seed-browse.sql`에 공구 100건 / 1만 건 시드 작성(상태·카테고리·마감 시각 분산, 한글 제목에 흔한 단어와 드문 단어 혼합) 후 로드한다.
- [ ] T032 `load/k6/browse.js` 작성: 목록 첫 페이지(일반·검색 흔한 단어·검색 드문 단어)를 `DATASET=100`, `DATASET=10000`으로 측정한다. SC-001(1만 건 p95 1초 이내), SC-007(100건 대비 2배 이내)을 확인하고 결과(환경 포함)를 `docs/benchmarks/browse-n-plus-one.md`에 적는다. 드문 검색어는 전체 스캔 비용이라 스파이크 #13이 재지 않은 부분이다.
- [ ] T033 quickstart.md 시나리오 1~21을 모두 실행해 기대 결과와 대조한다.
- [ ] T034 [P] `docs/spikes.md`의 003 미검증 가정 4건(`StatementInspector`, `LIKE ESCAPE`, 드문 검색어 전체 스캔, `default_batch_fetch_size`)이 T001·T008·T028·T032에서 확인되었는지 표시한다.
- [ ] T035 [P] 006이 같은 상세의 `myParticipation`에 입금·수령 확인 상태를 더하고 개설자 명단과 같은 쿼리를 쓴다는 점을 코드 주석으로 남긴다(`ParticipantView`, `GroupBuyDetail`).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1·2**: 001 구현 이후. T003·T004·T005·T006·T007은 서로 독립이라 병렬 가능, T002는 T004 전에 해도 된다.
- **US1 → US2 → US3**: 세 스토리는 `GroupBuyQueryService`·`GroupBuyQueryController`를 공유하므로 병렬로 진행하지 않는다.
- **Phase 6(N+1 개선)**: US2·US3의 순진한 구현과 기준선 기록(T020, T026)이 끝난 뒤.
- **Polish**: 원하는 스토리가 끝난 뒤.

### 태스크 간 의존

- T011은 T005(표시 상태 조건), T006(커서), T007(요청 파라미터) 이후. T012는 T011 이후. T013은 T012 이후.
- T018은 T016·T017 이후. T019는 T018 이후이며 001의 `GET` 제거를 포함한다.
- T020·T026(기준선 기록)은 순진한 구현 상태에서 해야 의미가 있다. T027 이후에는 기준선을 다시 잴 수 없다.

## Parallel Example

```bash
# Phase 1·2의 서로 다른 파일
T003 GroupBuy 연관관계 / T004 Participation 연관관계 / T005 DisplayStatus / T006 PageCursor / T007 GroupBuyListQuery

# US1 테스트 두 개
T008 ListGroupBuyIT / T009 PaginationConsistencyIT

# US2·US3 테스트와 DTO
T014 GroupBuyDetailIT / T015 QueryCountIT(B) / T016 DTO / T021 MyActivityIT / T022 QueryCountIT(A) / T023 MyActivityItem
```

## Implementation Strategy

1. Phase 1·2 → US1(목록)로 멈추고 검증 → US2(상세) → US3(내 활동).
2. **N+1 개선은 순진한 구현을 먼저 커밋하고 측정한 뒤에** 한다. 처음부터 최적화해 쓰면 전후 비교가 사라진다.
3. 시간 상한(헌법 I)이 걸리면 Polish의 k6 측정(T032)을 줄이되 N+1 기록(T020~T030)은 줄이지 않는다.

## Notes

- **(핵심 로직)** 태스크(T005, T006, T011, T012, T018, T024)는 키셋·공개 규칙·쿼리를 다룬다. 구현은 세션이 하고 승인 요약에 설명 체크 질문이 붙는다.
- 이 기능은 읽기 전용이다. 데이터를 바꾸는 코드를 추가하지 않는다.
- 각 태스크 또는 논리적 묶음 후 커밋한다. N+1 단계 커밋은 문서에 해시를 적어야 하므로 단계별로 분리한다.
- 체크포인트마다 멈춰서 스토리 단독 동작을 검증한다.
