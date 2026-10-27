---

description: "알림(앱 안 알림함) 구현 태스크"
---

# Tasks: 알림

**Input**: `/specs/007-notify/` 의 plan.md, spec.md, research.md, data-model.md, contracts/notification-api.md, quickstart.md

**Prerequisites**: plan.md, spec.md. **001·002·004 구현이 먼저 끝나 있어야 한다**: `GroupBuyEvents`(`GroupBuyChanged`/`GroupBuyCancelled`/`GroupBuyCompleted`), 이 이벤트를 발행하는 001·004의 서비스, `IntegrationTestBase`(`MutableClock`, `EventCollector`, `as(memberId)`), 002의 로그인 세션, 003의 키셋 커서 유틸. `GroupBuyFailed`는 005 구현이 있어야 발행된다. 005가 아직 없으면 T006에서 이벤트 정의만 준비하고, 모집 실패 알림 시험(시나리오 2)은 이벤트를 직접 발행해서 한다.

**Tests**: 이 기능에는 헌법 IV 필수 대상이 없다. 중복 방지, 실패 격리, 수신자 정확도, 소유 규칙은 Testcontainers(MySQL) 통합 테스트로 덮는다. 각 스토리에서 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다. 비동기 처리는 테스트에서 완료를 기다린다(폴링 또는 실행기 동기화).

**Organization**: 사용자 스토리별로 묶었다. 이 프로젝트는 Spec Kit의 `implement` 명령은 쓰지 않는다. 구현은 작업 세션(AI)이 하고, **일반 태스크는 Phase 끝에 묶음 승인, `(핵심 로직)` 태스크는 태스크마다 승인**을 받는다(2026-10-27 개정, `docs/sessions/README.md`). **(핵심 로직)** 표시 태스크는 승인 요약에 설명 체크 질문이 붙는다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 태스크에 의존하지 않아 병렬 가능
- **[Story]**: US1(사건 → 알림 생성), US2(알림함 보기), US3(중복·대상 정확도)
- 경로의 `main/`은 `src/main/java/com/camgong/`, `test/`는 `src/test/java/com/camgong/`, `res/`는 `src/main/resources/` 를 뜻한다. 이 기능의 새 코드는 최상위 패키지 `notification/`에 둔다. 의존은 `notification → groupbuy`(이벤트 읽기) 한 방향뿐이다.

## Phase 1: Setup

**Purpose**: 테이블, 설정, 비동기 실행기, 응답 모양

- [ ] T001 `res/schema.sql`에 `notification` 테이블 추가(data-model.md): `id BIGINT AUTO_INCREMENT PRIMARY KEY`, `event_id CHAR(36) NOT NULL`, `recipient_id BIGINT NOT NULL`, `type VARCHAR(30) NOT NULL`, `group_buy_id BIGINT NOT NULL`(FK 없음 — 취소된 공구도 알림이 남는다), `group_buy_title VARCHAR(100) NOT NULL`, `changed_fields VARCHAR(200) NULL`, `created_at DATETIME(6) NOT NULL`, `read_at DATETIME(6) NULL`. 제약: `UNIQUE (event_id, recipient_id)`, `KEY (recipient_id, id)`, `KEY (recipient_id, read_at)`, `KEY (created_at)`. `CREATE TABLE IF NOT EXISTS` 정의 안의 `KEY` 절로 넣는다(MySQL은 `CREATE INDEX IF NOT EXISTS`를 지원하지 않는 것으로 알고 있다 — 구현 시 확인). 제목 길이 100은 001의 `title` 상한과 같아야 한다 — 001 data-model을 확인하고 다르면 멈춰서 알린다.
- [ ] T002 [P] `res/application.yml`에 설정 추가(contracts/notification-api.md 설정 표): `camgong.notification.async.core-size`(2) / `max-size`(4) / `queue-capacity`(1000), `insert-chunk-size`(500), `retry-attempts`(3), `retention`(30d), `cleanup.enabled`(true) / `cleanup.interval`(1d). 바인딩용 `@ConfigurationProperties` record를 `main/notification/NotificationProperties.java`에 둔다. 테스트 프로파일은 `cleanup.enabled=false`.
- [ ] T003 [P] `main/notification/AsyncConfig.java`: `@EnableAsync`와 알림 전용 `ThreadPoolTaskExecutor`(T002 설정). 큐가 가득 차면 **거부하고 WARN 로그**(`AbortPolicy`를 잡아 로그) — `CallerRunsPolicy`를 쓰지 않는다(원 요청을 느리게 해 FR-012를 어긴다, research 결정 10). 거부된 알림은 유실로 취급한다(허용된 한계). 주석: `// ponytail: 가득 차면 유실. 신뢰성이 필요하면 아웃박스(research 결정 1의 C)`
- [ ] T004 [P] `main/notification/NotificationType.java`(enum `GROUPBUY_COMPLETED`, `GROUPBUY_FAILED`, `GROUPBUY_CHANGED`, `GROUPBUY_CANCELLED`)와 `NotificationView.java`(응답 record: `id`, `type`, `groupBuyId`, `groupBuyTitle`, `changedFields`(배열 또는 null), `createdAt`(오프셋 포함 ISO-8601, KST), `read`, `linkable`). `linkable`은 `GROUPBUY_CANCELLED`만 `false`.

---

## Phase 2: Foundational (모든 스토리의 선행 조건)

**Purpose**: 이벤트에 `eventId`·제목 추가(교차 변경), 대량 삽입, 이벤트 리스너

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 스토리 작업을 시작하지 않는다.

- [ ] T005 [P] **중복 방지 스파이크(미검증 가정 2)**: 임시 테스트(`test/notification/BatchInsertSpikeIT.java`)로 `INSERT … ON DUPLICATE KEY UPDATE id = id` 배치가 `rewriteBatchedStatements` 없이 동작하고 중복을 오류 없이 무시하는지 확인한다. 결과(된다/안 된다, 사용한 연결 속성)를 `docs/spikes.md` 표에 한 줄로 남기는 것은 관제탑이 하니 세션은 승인 요약에 적는다. 안 되면 대안을 정하지 말고 멈춰서 알린다. 스파이크 테스트는 T007의 실제 테스트로 바꾸거나 지운다.
- [ ] T006 `main/groupbuy/GroupBuyEvents.java`의 이벤트 record에 `String eventId`(`UUID.randomUUID().toString()`, 발행 시 한 번 생성)와 `String groupBuyTitle`(사건 시점 제목 스냅샷; 수정은 수정 후 제목)을 추가한다(research 결정 2, data-model 이벤트 변경 표): `GroupBuyChanged`, `GroupBuyCancelled`, `GroupBuyCompleted`, 그리고 `GroupBuyFailed`. 발행하는 001·004·005 서비스의 호출부와 기존 테스트(`EventCollector` 단언 등)를 함께 고친다. `GroupBuyFailed`는 005가 아직 구현되지 않았다면 005 T003이 이 두 필드를 처음부터 갖게 하고 지금은 정의만 추가한다. 기존 테스트가 깨지면 이 변경 때문인지 확인하고 고친다(미검증 가정 4). 취소 이벤트는 취소 트랜잭션 안에서 제목을 읽어 담는다(취소된 공구는 이후 읽을 수 없다).
- [ ] T007 `main/notification/NotificationWriter.java` **(핵심 로직)**: `write(List<NotificationRow>)`를 `insert-chunk-size`(500)씩 나눠 `INSERT INTO notification (event_id, recipient_id, type, group_buy_id, group_buy_title, changed_fields, created_at) VALUES (…) ON DUPLICATE KEY UPDATE id = id`를 JDBC 배치로 실행한다. `JdbcClient`에 배치 API가 없으면 `JdbcTemplate.batchUpdate`를 쓴다(JPA `saveAll`은 `IDENTITY` 키에서 배치가 꺼진다). 청크 하나가 실패하면 **그 청크만** 행마다 단건 삽입으로 격리해 실패한 행만 남긴다. 실패한 행은 최대 `retry-attempts`(3)번 지수 대기(1초, 4초, 16초)로 다시 시도하고, 그래도 실패하면 WARN 로그(`notification-failed eventId=… recipientId=… reason=…`)를 남기고 포기한다. 중복 무시 판정은 영향 행 수에 기대지 않는다(`useAffectedRows` 의존 금지). 각 청크는 자기 트랜잭션이다.
- [ ] T008 `main/notification/NotificationEventListener.java` **(핵심 로직)**: 네 이벤트를 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`(T003의 실행기)로 받아 `NotificationRow`를 만들고 `NotificationWriter.write`를 호출한다. 알림 `type`과 대상은 contracts의 내부 계약 표를 따른다. 대상은 **이벤트의 `recipientIds`만** 쓴다(공구나 참여 테이블을 다시 읽지 않는다 — 사건 시점 참여자가 이미 담겨 있다). `recipientIds`가 비어 있으면 아무것도 하지 않는다. `GroupBuyChanged`는 `changedFields`를 쉼표로 이은 텍스트로 저장한다. 알림 내용은 제목·종류·바뀐 항목 이름·시각뿐이다(다른 회원 정보 금지, FR-004). 리스너가 던지는 예외는 원 요청에 영향이 없어야 하므로 잡아서 로그로 남긴다. `created_at`은 앱 `Clock`.

**Checkpoint**: 이벤트를 직접 발행하면 대상자마다 `notification` 행이 한 번 생긴다.

---

## Phase 3: User Story 1 - 공구에 일어난 중요한 변화를 알림으로 받기 (Priority: P1) 🎯 MVP

**Goal**: 모집 완료·모집 실패·수정·취소가 일어나면 받아야 할 사람만 정해진 내용의 알림을 받는다.

**Independent Test**: 네 사건을 각각 일으켜 알림을 받아야 하는 사람만 정해진 내용의 알림을 받는지 확인한다.

### Tests for User Story 1 ⚠️ (먼저 작성하고 실패 확인)

- [ ] T009 [P] [US1] `test/notification/NotificationCreateIT.java`: 시나리오 1(마지막 수량 참여로 `COMPLETED` → 개설자·2·3에게 `GROUPBUY_COMPLETED` 각 1건, 제목 포함), 2(`GroupBuyFailed` → 개설자·2·3에게 `GROUPBUY_FAILED`; 005가 없으면 이벤트 직접 발행), 3(가격·마감 수정 → 2·3에게 `GROUPBUY_CHANGED` 각 1건, `changedFields=["unitPrice","deadline"]`, 개설자는 못 받음), 4(취소 → 2·3에게 `GROUPBUY_CANCELLED` 각 1건, 취소 뒤에도 제목이 알림에 남음), 5(오픈채팅 링크만 수정 → 알림 없음), 6(개설자만 있는 공구의 수정·취소 → 알림 없음), 17(검증 실패로 롤백된 수정 → 알림 없음), 21(참여·수량 변경·참여 취소 → 알림 없음), 22(알림 행·응답에 다른 회원의 이름·수량·링크·이메일이 없음)
- [ ] T010 [P] [US1] `test/notification/NotificationEventListenerTest.java`: 이벤트 `recipientIds`가 빈 목록이면 쓰기 호출 없음, 알림 내용에 `recipientIds` 밖의 회원이 들어가지 않음, 연속 수정 두 번(서로 다른 `eventId`)은 각각 알림을 만든다(시나리오 15).

### Implementation for User Story 1

- [ ] T011 [US1] T008의 리스너가 발행 지점(001 수정·취소, 004 마지막 수량 참여, 005 마감 확정)과 실제로 연결되는지 통합 테스트로 확인하고, 연결이 안 되면 원인을 고친다(예: 이벤트를 발행하는 트랜잭션 밖에서 `publishEvent`를 호출해 `AFTER_COMMIT`이 안 도는 경우, 비동기 컨텍스트 설정 누락). 새 코드가 필요 없으면 확인 결과를 커밋 메시지에 남긴다.

**Checkpoint**: US1이 단독으로 동작한다. T009·T010이 통과한다.

---

## Phase 4: User Story 2 - 알림을 어디서든 놓치지 않고 확인하기 (Priority: P2)

**Goal**: 회원이 자기 알림을 최근 순으로 보고, 읽지 않은 개수를 알고, 눌러서 읽음 처리한다.

**Independent Test**: 알림이 쌓인 회원이 목록을 열어 최근 순으로 보고, 하나를 읽음 처리하며, 타인의 알림은 보이지 않는지 확인한다.

### Tests for User Story 2 ⚠️

- [ ] T012 [P] [US2] `test/notification/NotificationInboxIT.java`: 시나리오 7(`GET /api/notifications` 최근 순, `read=false` 구분, 다른 회원의 알림 없음, 키셋 `cursor`/`size`, `size` 범위 밖·잘못된 커서 400), 8(`PUT …/{id}/read` → 200 `read=true`, 다시 눌러도 200이고 읽은 시각 불변 — `MutableClock`을 움직여 확인, 읽지 않은 개수 1 감소), 9(`GET …/unread-count`가 읽지 않은 수와 일치), 10(취소 알림 항목은 `linkable=false`, 내용만), 11(헤더 없음/비로그인 401, 다른 회원의 알림 id에 `PUT …/read` → 404이고 없는 id와 같은 응답)

### Implementation for User Story 2

- [ ] T013 [US2] `main/notification/NotificationService.java`: 목록(`recipient_id = :me [AND id < :cursor] ORDER BY id DESC LIMIT :size + 1`, 003의 불투명 커서 유틸 재사용, 전체 건수 없음), 읽지 않은 개수(`COUNT(*) WHERE recipient_id = :me AND read_at IS NULL`), 읽음 처리 **(핵심 로직)**: `UPDATE notification SET read_at = COALESCE(read_at, :now) WHERE id = :id AND recipient_id = :me` 후 영향 행이 0이면 `SELECT`로 존재와 소유를 확인해 "없음 또는 남의 것"은 같은 404, "이미 읽음"은 200(소유 여부를 알려주지 않는다). 모든 쿼리의 조건에 `recipient_id = :me`를 넣는다(FR-007). 알림함은 `JdbcClient`로 읽는다(조인이 없다).
- [ ] T014 [US2] `main/notification/NotificationController.java`: `GET /api/notifications?cursor=&size=`, `GET /api/notifications/unread-count`, `PUT /api/notifications/{id}/read`. 요청자는 002의 인증 결과에서 얻는다. 오류 본문은 001의 `ProblemDetail` + `errors[]`. `GET /api/notifications` 응답 필드는 contracts와 정확히 같게 한다(`changedFields`는 항목 이름 배열, 저장은 쉼표 텍스트).

**Checkpoint**: T012가 통과한다.

---

## Phase 5: User Story 3 - 같은 알림이 중복되거나 엉뚱한 사람에게 가지 않기 (Priority: P2)

**Goal**: 한 사건에 대해 대상자는 정확히 한 번만 알림을 받고, 자격 없는 사람은 받지 않는다.

**Independent Test**: 같은 사건을 중복 처리해도 알림이 한 번만 만들어지는지, 참여를 취소하고 나간 사람과 무관한 회원이 받지 않는지 확인한다.

### Tests for User Story 3 ⚠️

- [ ] T015 [P] [US3] `test/notification/NotificationDedupIT.java`: 시나리오 12(같은 이벤트를 순차로 두 번, 동시에 두 스레드로 → 대상자마다 알림 1건, 동시 처리를 1,000번 반복해도 중복 0 — **SC-003**), 13(참여했다가 취소하고 나간 회원 4는 그 공구의 어떤 사건에도 알림 없음), 14(참여한 적 없는 회원 5는 알림 없음)

### Implementation for User Story 3

- [ ] T016 [US3] 중복 방지가 `UNIQUE (event_id, recipient_id)`(T001)와 `ON DUPLICATE KEY UPDATE id = id`(T007)만으로 성립하는지 T015로 확인하고, 성립하지 않으면(예: `eventId`가 발행 때마다 새로 생기지 않거나 같은 이벤트 객체가 다른 `eventId`로 재생성) 원인을 수정한다. 나간 참여자·무관한 회원이 받지 않는 것은 `recipientIds`가 사건 시점 참여자로 채워지기 때문이므로(T006), 이벤트를 만드는 곳(001·004·005)이 참여 취소한 회원을 목록에서 빼는지 확인한다. 새 코드가 필요 없을 수도 있다 — 그 경우 확인 결과를 커밋 메시지에 남긴다.

**Checkpoint**: T015가 반복 실행에서도 중복 0건으로 통과한다.

---

## Phase 6: 안정성과 정리 (횡단 요구: FR-009, FR-010, FR-012)

- [ ] T017 [P] `test/notification/NotificationResilienceIT.java`: 시나리오 16(수신자 중 한 명의 삽입을 일부러 실패시킴 — `NotificationWriter`의 삽입을 테스트용으로 가로채 특정 `recipient_id`에서 예외 → 나머지 수신자의 알림은 모두 생성, 실패 건은 재시도 후 성공하면 정확히 1건, 재시도에서 중복 없음. SC-007), 19(참여자 1,000명 공구의 모집 완료 → 5분 안에 1,000건 생성, 중복 0), 18(수신자 1,000명 사건을 일으킨 요청의 응답 시간이 알림이 없을 때와 비슷 — 95%가 1초 이내, 요청 응답이 알림 완료를 기다리지 않음)
- [ ] T018 [P] `test/notification/NotificationCleanupIT.java`: 시나리오 20(`MutableClock`을 31일 뒤로 두고 정리 실행 → 30일 지난 알림 삭제, 최근 알림 유지, 반복 실행해도 안전, 정리 작업 두 개가 동시에 돌아도 오류 없음)
- [ ] T019 `main/notification/NotificationCleanup.java`: `@Scheduled(fixedDelayString = "${camgong.notification.cleanup.interval:1d}")`로 `DELETE FROM notification WHERE created_at < :now - INTERVAL 30 DAY LIMIT 1000`을 영향 행이 0이 될 때까지 반복. `camgong.notification.cleanup.enabled=false`면 등록하지 않는다. 005의 스케줄러와 같은 패턴(`fixedDelay`, 설정으로 끄기). 보관 기간은 `retention` 설정에서 읽는다. 정리 서비스 메서드를 테스트가 직접 호출할 수 있게 스케줄러 클래스와 분리한다.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T020 quickstart.md 시나리오 1~22를 모두 실행해 기대 결과와 대조한다.
- [ ] T021 측정(`docs/benchmarks/notify-bulk.md`): 수신자 1,000명 사건의 알림 생성 완료까지 시간(5분 이내, SC-005)과 그동안 사건을 일으킨 요청의 응답 시간(95% 1초 이내), 알림 목록 응답 시간(95% 1초 이내, SC-006)을 환경과 함께 기록한다. 헌법 II 측정 대상은 아니므로 기록용이다.
- [ ] T022 [P] research.md 끝의 007 미검증 가정(리스너 새 트랜잭션 커밋, `ON DUPLICATE KEY UPDATE id = id` 배치, 1,000명 5분, 기존 테스트가 `eventId` 추가로 깨지지 않음)이 T005·T006·T008·T017에서 확인되었는지 표시한다.
- [ ] T023 [P] 알림에 다른 회원 정보가 담기지 않는 것(SC-004)을 `NotificationCreateIT`의 응답 검사와 컬럼 구성(T001)으로 다시 확인한다. 알림이 로그에 회원 이메일·이름을 남기지 않는지도 `WARN` 로그 형식을 점검한다.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1·2**: 001·002·004 구현 이후. T002·T003·T004·T005는 병렬 가능. T006은 T005와 독립. T007은 T001·T005·T006 이후. T008은 T003·T004·T006·T007 이후.
- **US1 → US2 → US3** 순서. US2는 US1과 독립(알림 행만 있으면 되므로 시드로 시험 가능)이지만 US1 뒤에 진행한다. US3은 US1의 리스너 위에서 검증한다.
- **Phase 6·7**: 원하는 스토리가 끝난 뒤. T019는 T018과 함께 진행한다(테스트 먼저).

### 태스크 간 의존

- T011은 T008 이후. T013은 T004 이후. T014는 T013 이후.
- T015·T017은 T008 이후이며 구현 전에 작성해 실패를 확인한다.

## Parallel Example

```bash
# Phase 1의 서로 다른 파일
T002 설정 / T003 AsyncConfig / T004 enum·응답 record / T005 스파이크

# 스토리별 테스트
T009 NotificationCreateIT / T012 NotificationInboxIT / T015 NotificationDedupIT / T017 NotificationResilienceIT / T018 NotificationCleanupIT
```

## Implementation Strategy

1. Phase 1·2 → US1로 멈추고 검증(MVP: 네 사건이 알림 행을 만든다) → US2(알림함) → US3(중복·대상 정확도) → 안정성·정리.
2. 줄여야 하면 정리 작업(T018·T019)을 가장 먼저 줄이고(알림이 쌓이기만 한다), 그다음 대량 시험(T017의 시나리오 19·18)의 반복 횟수를 줄인다. 중복 방지(T015)와 수신자 정확도는 줄이지 않는다.

## Notes

- **(핵심 로직)** 태스크(T007, T008, T013)는 대량 삽입·중복 무시·실패 격리·비동기 리스너·읽음 처리 소유 규칙을 다룬다. 구현은 세션이 하고 승인 요약에 설명 체크 질문이 붙는다(research 결정 1~4: `@Async`를 쓰지 않으면 FR-012가 깨지는 이유, 연속 수정이 서로 다른 사건으로 구분되는 이유와 재처리가 한 번만 들어가는 이유, 청크 단건 격리가 중복을 만들지 않는 이유, 재시도가 같은 알림을 두 번 만들지 않는 이유).
- 이벤트는 커밋 후 비동기로 받아 서버가 멈추거나 큐가 가득 차면 유실될 수 있다(허용된 한계). 중복 생성은 금지다.
- 이 기능의 응답 필드(`changedFields`, `linkable`)를 프론트(008 S10)가 쓴다. 필드 이름을 바꾸지 않는다.
- 각 태스크 또는 논리적 묶음 후 커밋한다.
