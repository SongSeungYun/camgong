---

description: "마감 확정(모집 실패 처리) 구현 태스크"
---

# Tasks: 마감 확정(모집 실패 처리)

**Input**: `/specs/005-close-groupbuy/` 의 plan.md, spec.md, research.md, data-model.md, contracts/events-and-config.md, quickstart.md

**Prerequisites**: plan.md, spec.md. **001 구현이 먼저 끝나 있어야 한다**: `GroupBuy`, `GroupBuyStatus`, `GroupBuyRepository`, `Participation`/`ParticipationRepository`, `GroupBuyEvents`, `GroupBuyEventLogger`, `Clock` 빈, `IntegrationTestBase`(`MutableClock`). 004의 참여·003의 조회는 선행 조건이 아니지만, 경쟁 테스트(T014)와 표시 확인은 004·003이 있으면 API로, 없으면 SQL 시드로 한다.

**Tests**: 헌법 IV의 필수 대상 — **스케줄러 중복 실행 방지**, 마감 성공/실패 확정, 모집 완료/실패 전환 경쟁 — 의 Testcontainers(MySQL) 통합 테스트를 포함한다. 각 스토리에서 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.

**Organization**: 사용자 스토리별로 묶었다. 이 프로젝트는 Spec Kit의 `implement` 명령은 쓰지 않는다. 구현은 작업 세션(AI)이 태스크 단위로 하고, 사용자가 승인한 뒤에 커밋한다(2026-10-27 변경, `docs/sessions/README.md`). **(핵심 로직)** 표시 태스크는 승인 요약에 설명 체크 질문이 붙는다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 태스크에 의존하지 않아 병렬 가능
- **[Story]**: US1(마감 후 자동 확정), US2(중복 실행 방지), US3(경계 경쟁), US4(재시작·일부 실패)
- 경로의 `main/`은 `src/main/java/com/camgong/`, `test/`는 `src/test/java/com/camgong/`, `res/`는 `src/main/resources/` 를 뜻한다. 이 기능의 새 코드는 하위 패키지 `groupbuy/close/`에 둔다. 애그리거트 수정은 `groupbuy/`에 한다.

## Phase 1: Setup

**Purpose**: 상태 값, 인덱스, 설정, 이벤트

- [ ] T001 [P] `main/groupbuy/GroupBuyStatus.java`에 `FAILED` 추가. 컬럼 `status`는 `VARCHAR(20)`이라 스키마 변경 없음. `FAILED`에서 나가는 전이는 없다.
- [ ] T002 `res/schema.sql`의 `group_buy` 정의에 인덱스 `idx_group_buy_status_deadline (status, deadline)` 추가. `schema.sql`은 앱 시작 때마다 재실행되고 MySQL은 `CREATE INDEX IF NOT EXISTS`를 지원하지 않는 것으로 알고 있으므로(구현 시 확인), `CREATE TABLE IF NOT EXISTS` 정의 안의 `KEY` 절로 넣는다.
- [ ] T003 [P] `main/groupbuy/GroupBuyEvents.java`에 `GroupBuyFailed(groupBuyId, recipientIds)` record 추가(`recipientIds`는 개설자를 포함한 모든 참여 행의 회원 id, 중복 없음). `main/groupbuy/GroupBuyEventLogger.java`에 `@TransactionalEventListener(AFTER_COMMIT)`로 `GroupBuyFailed`를 로그로 남기는 핸들러 추가. 주석: `// ponytail: 커밋 직후 서버가 멈추면 유실될 수 있음, 알림 기능이 소비`
- [ ] T004 [P] `res/application.yml`에 설정 추가(contracts/events-and-config.md): `camgong.scheduler.close.enabled`(기본 `true`), `interval`(`30s`), `margin`(`1s`), `batch-size`(`100`), `max-rounds`(`50`). 테스트 프로파일은 `enabled=false`로 두고 서비스를 직접 호출한다. 설정 바인딩용 `@ConfigurationProperties` record를 `main/groupbuy/close/CloseProperties.java`에 둔다.
- [ ] T005 [P] `main/groupbuy/close/CloseStats.java`: record `closed`, `skipped`, `failed`, `rounds`, `durationMs`.
- [ ] T006 [P] `test/support/IntegrationTestBase.java`에 시드 헬퍼 추가: 상태·마감·수량을 지정해 공구를 만드는 메서드, 개설자 + 참여자 N명을 넣는 메서드, 마감이 지난 미충족 공구를 대량으로 넣는 메서드(100, 1,000건).

---

## Phase 2: Foundational (모든 스토리의 선행 조건)

**Purpose**: 상태 전이, 후보·선점 쿼리, 건별 확정

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 스토리 작업을 시작하지 않는다.

- [ ] T007 `main/groupbuy/GroupBuy.java`에 `fail(Instant now)` **(핵심 로직)**: `status == OPEN`일 때만 `FAILED`로 바꾸고 `updatedAt = now`. 그 외 상태, 또는 확보 = 총 수량이면 예외(불변식 I7, I10). `FAILED`는 종료 상태다.
- [ ] T008 `main/groupbuy/GroupBuyRepository.java`에 쿼리 두 개 추가 **(핵심 로직)**:
  - 후보 id 조회(락 없음): `SELECT id FROM group_buy WHERE status='OPEN' AND deadline <= :cutoff AND secured_quantity < total_quantity ORDER BY deadline LIMIT :batchSize`
  - 건별 선점(네이티브): `SELECT * FROM group_buy WHERE id = :id AND status = 'OPEN' AND deadline <= :cutoff AND secured_quantity < total_quantity FOR UPDATE SKIP LOCKED` — 영속 엔티티로 돌아와야 한다. `:cutoff`는 `now − margin(1초)`. **선점은 반드시 이렇게 id 하나를 지정한다.** `ORDER BY deadline LIMIT 1 … FOR UPDATE SKIP LOCKED`처럼 범위를 훑어 한 건을 잡는 형태는 기본 격리 수준(REPEATABLE READ)에서 갭 락 때문에 다른 워커의 `status` 갱신이 락 대기 시간 초과로 실패했다(spikes 'Spring 수준 검증' 5'). id 지정 선점은 같은 조건에서 통과했다(5c).
- [ ] T009 `main/groupbuy/close/GroupBuyCloser.java` **(핵심 로직)**: 별도 빈의 `closeOne(id, cutoff, now)`. `@Transactional`로 선점 쿼리(T008) 실행 → 비어 있으면 `SKIPPED` → 있으면 `fail(now)`, 개설자 포함 모든 참여 행의 회원 id로 `GroupBuyFailed` 발행(같은 트랜잭션, 커밋 후 소비), `CLOSED` 반환. 서비스와 **다른 클래스의 빈**이어야 프록시를 거쳐 건마다 새 트랜잭션이 시작된다.

**Checkpoint**: `closeOne`을 직접 호출해 한 공구가 `FAILED`가 되고 이벤트가 한 번 나간다.

---

## Phase 3: User Story 1 - 마감 후 자동 확정 (Priority: P1) 🎯 MVP

**Goal**: 마감 시각이 지난 미충족 공구가 자동으로 모집 실패가 되고 알림 이벤트가 나간다.

**Independent Test**: 마감이 지난 미충족 공구, 마감 전 공구, 모집 완료 공구, 취소 공구를 두고 확정을 실행해 미충족 공구만 `FAILED`가 되는지, 이벤트가 한 번인지 확인한다.

### Tests for User Story 1 ⚠️ (먼저 작성하고 실패 확인)

- [ ] T010 [P] [US1] `test/groupbuy/close/CloseExpiredIT.java`: quickstart 시나리오 1(`now = T + 2초`에서 확정 → `FAILED`, 이후 참여·수정·취소 거부), 2(`now = T − 1초`에서 변화 없음), 3(완료·취소·이미 실패인 공구는 변화 없음·이벤트 없음), 4(`now = T`에서는 `OPEN` 유지·`now = T + 1초`에서 확정), 5(개설자 + 참여자 2명 → `GroupBuyFailed` 1건, 대상 3명), 6(참여 기록 보존, 003 표시와 오픈채팅 링크 비공개), 7(참여자 없이 개설자만 → `FAILED`, 대상 1명), 17(로그 `close-run closed=1 skipped=0 failed=0 …` 한 줄)

### Implementation for User Story 1

- [ ] T011 [US1] `main/groupbuy/close/GroupBuyCloseService.java`의 `closeExpired()` **(핵심 로직)**: `cutoff = now − margin`으로 후보 id를 `batch-size`씩 조회(T008)하고 후보마다 `GroupBuyCloser.closeOne`을 호출. 라운드 반복 조건: 후보가 없으면 끝, 한 라운드에서 확정한 공구가 0건이면 끝(후보가 전부 다른 인스턴스에 잡혀 있을 때 무한 반복 방지), 최대 `max-rounds`. 마감 시각이 이른 공구부터 처리한다. `CloseStats`를 돌려준다.
- [ ] T012 [US1] 로그 기록(FR-012): 후보가 있었던 실행이 끝나면 `INFO close-run closed=… skipped=… failed=… rounds=… durationMs=…` 한 줄, 후보가 하나도 없으면 로그를 남기지 않는다. 한 공구가 예외로 실패하면 `WARN close-failed groupBuyId=… reason=…`.
- [ ] T013 [US1] `main/groupbuy/close/CloseExpiredScheduler.java`: `@Scheduled(fixedDelayString = "${camgong.scheduler.close.interval:30s}")`로 `closeExpired()` 호출, `camgong.scheduler.close.enabled=false`면 등록하지 않음, 앱 시작 직후 바로 한 번 실행되도록 초기 지연 0. `@EnableScheduling` 설정. `fixedRate`가 아니라 `fixedDelay`(이전 실행이 끝난 뒤 간격을 센다).

**Checkpoint**: US1이 단독으로 동작한다. T010이 통과한다.

---

## Phase 4: User Story 2 - 여러 곳에서 동시에 실행돼도 한 번만 확정 (Priority: P2)

**Goal**: 서버가 여러 대이거나 실행이 겹쳐도 같은 공구는 한 번만 확정되고 이벤트도 한 번이다. **헌법 IV 필수 테스트.**

**Independent Test**: 마감이 지난 미충족 공구 100개에 확정 작업 5개를 동시에 돌려 공구마다 확정과 이벤트가 정확히 한 번인지 확인한다.

### Tests for User Story 2 ⚠️

- [ ] T014 [P] [US2] `test/groupbuy/close/CloseConcurrencyIT.java`: 시나리오 8(마감된 공구 100개, 스레드 5개가 각자 커넥션으로 동시에 `closeExpired()` → 100개 모두 `FAILED`, 각 공구의 확정과 이벤트가 정확히 1회, 이벤트 수집기로 센다. CI는 100회 반복, 수동으로 1,000회), 9(한 실행이 끝나기 전 다음 실행 시작 → 같은 공구를 두 번 처리하지 않음), 10(이미 `FAILED`인 공구에 다시 실행 → 아무 일도 없고 이벤트 재발행 없음)

### Implementation for User Story 2

- [ ] T015 [US2] 중복 방지가 T008의 선점 쿼리와 T009의 건별 트랜잭션만으로 성립하는지 확인하고, 성립하지 않으면(예: 같은 클래스 호출로 트랜잭션이 안 열림, 후보 조회와 선점 사이 상태 변화) 원인을 수정한다. 새 코드가 필요 없을 수도 있다 — 그 경우 확인 결과를 커밋 메시지에 남긴다. `useAffectedRows`는 이 쿼리에 영향이 없다.

**Checkpoint**: T014가 100회, 1,000회 반복에서도 위반 0건으로 통과한다.

---

## Phase 5: User Story 3 - 마지막 참여·수정·취소와 마감이 동시에 와도 일관 (Priority: P2)

**Goal**: 마감 직전·직후에 마지막 수량 참여, 마감 연장, 취소가 확정과 동시에 도착해도 처리 시각 기준으로 일관된다.

**Independent Test**: 마감 직전 공구에 대해 마지막 수량 참여와 확정을 동시에 시작하는 시험을 반복해 공구가 모집 완료 또는 모집 실패 중 정확히 하나가 되는지 확인한다.

### Tests for User Story 3 ⚠️

- [ ] T016 [P] [US3] `test/groupbuy/close/CloseRaceIT.java`: 시나리오 11(남은 1인 공구에서 마지막 수량 참여와 확정을 동시에: 참여가 마감 전에 처리되면 `COMPLETED`(확정 대상 아님), 마감 후에 처리되면 참여 409 후 `FAILED`; 두 상태를 동시에 갖는 공구 0건, `MutableClock`으로 처리 시각을 고정하고 1,000번 반복), 12(마감 연장 요청과 확정 동시에: 연장이 마감 전에 처리되면 `FAILED` 아님, 이후면 연장 409 후 `FAILED`), 13(개설자 취소와 확정 동시에: `CANCELLED`와 `FAILED` 중 정확히 하나). 004 참여·001 수정/취소 구현이 있어야 하며 서버 쪽 요청은 서비스 직접 호출로 한다.

### Implementation for User Story 3

- [ ] T017 [US3] 경계 규칙 점검 **(핵심 로직)**: 참여·수정·취소는 `deadline <= now`부터 즉시 거부하고(001 T031·T032, 004 게이트), 확정은 `deadline <= now − margin`부터라는 두 판정이 모두 **요청을 처리하는 순간의 `Clock`**을 쓰는지, 그리고 공구 행 락 안에서 판정하는지 확인한다. 어긋나면 해당 파일을 수정한다(001·004 구현의 해당 위치). 마감~확정 사이(최대 약 31초)는 '마감됨(확정 대기)'다.

**Checkpoint**: T016이 반복 실행에서도 한 공구가 두 종료 상태를 갖는 경우 0건이다.

---

## Phase 6: User Story 4 - 재시작·일부 실패 (Priority: P3)

**Goal**: 멈췄다 재시작하면 밀린 공구를 모두 처리하고, 일부 공구의 실패가 나머지를 막지 않는다.

**Independent Test**: 스케줄러를 끈 채 여러 공구를 마감시키고 재시작해 모두 처리되는지, 일부 공구 처리에 실패를 일으켜도 나머지가 처리되는지 확인한다.

### Tests for User Story 4 ⚠️

- [ ] T018 [P] [US4] `test/groupbuy/close/CloseResilienceIT.java`: 시나리오 14(공구 10개 중 1개의 확정이 예외 → 9개 `FAILED`, 1개는 `OPEN` 유지·WARN 로그, 다음 실행에서 성공하면 이벤트 1회; 예외는 이벤트 리스너나 `GroupBuyCloser`를 테스트용으로 가로채 유발), 15(스케줄러를 끈 채 50개 마감 후 `closeExpired()` → 50개 `FAILED`, 마감 시각이 이른 순서로 처리)
- [ ] T019 [P] [US4] `test/groupbuy/close/CloseSchedulerWiringTest.java`: 시나리오 16(`enabled=true`로 컨텍스트를 띄우고 마감된 공구를 두면 주기 안에 스스로 확정), `enabled=false`면 스케줄러가 등록되지 않음.

### Implementation for User Story 4

- [ ] T020 [US4] `GroupBuyCloseService`(T011)에서 `closeOne`의 예외를 잡아 `failed`로 세고 WARN 로그를 남긴 뒤 **다음 공구로 진행**하도록 확인·보강한다. 예외로 롤백된 공구는 다음 실행의 후보가 되어 재시도되고, 재시도에서 확정과 이벤트가 중복되지 않는다.

**Checkpoint**: 네 스토리가 모두 통과한다.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T021 quickstart.md 시나리오 1~17을 모두 실행해 기대 결과와 대조한다.
- [ ] T022 측정(`docs/benchmarks/close-batch.md`): 확정 지연(마감 시각과 `FAILED`가 된 시각의 차이, 스케줄러를 켠 채 50개를 무작위 시각에 마감 → 95%가 1분 이내, SC-001), 대량 처리(공구 1,000건을 같은 시각에 마감시키고 완료까지 시간 → 10분 이내, 그동안 목록·상세 조회 95%가 1초 이내, SC-007, k6 `load/k6/list-during-close.js`)를 환경과 함께 기록한다. 헌법 II 측정 대상은 아니므로 기록용이다.
- [ ] T023 [P] `EXPLAIN`으로 후보 조회가 `idx_group_buy_status_deadline`을 타는지 확인하고 결과를 `docs/spikes.md`의 005 미검증 가정 표기에 남긴다.
- [ ] T024 [P] research.md 끝의 005 미검증 가정(네이티브 `SKIP LOCKED`가 영속 엔티티로 돌아오는지, 별도 빈 `@Transactional`이 건마다 새 트랜잭션을 여는지, 인덱스 사용)이 T009·T015·T023에서 확인되었는지 표시한다.
- [ ] T025 [P] 헌법 IV 필수 대상(스케줄러 중복 실행 방지, 마감 성공/실패 확정, 모집 완료/실패 전환 경쟁)이 통합 테스트로 존재하고 통과하는지 확인한다.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1·2**: 001 구현 이후. T001·T003·T004·T005·T006은 병렬 가능. T007은 T001 이후, T008은 T001·T002 이후, T009는 T003·T007·T008 이후.
- **US1 → US2 → US3 → US4** 순서로 진행한다. US2는 US1의 서비스 위에서 검증하고, US3은 001·004 구현에 의존한다. US4는 US1의 서비스를 보강한다.
- **Polish**: 원하는 스토리가 끝난 뒤.

### 태스크 간 의존

- T011은 T008(쿼리), T009(건별 확정), T005(통계), T004(설정) 이후. T013은 T011 이후.
- T014는 T011 이후이며 구현 전에 작성해 실패를 확인한다.
- T016은 001 T031·T032(수정·취소)와 004의 게이트(T007)가 있어야 한다.

## Parallel Example

```bash
# Phase 1의 서로 다른 파일
T001 FAILED 추가 / T003 이벤트·로거 / T004 설정 / T005 CloseStats / T006 테스트 시드

# 스토리별 테스트
T010 CloseExpiredIT / T014 CloseConcurrencyIT / T016 CloseRaceIT / T018 CloseResilienceIT / T019 CloseSchedulerWiringTest
```

## Implementation Strategy

1. Phase 1·2 → US1로 멈추고 검증 → US2(헌법 IV 필수 테스트) → US3 → US4.
2. 정확성은 통합 테스트로 증명하고 지연·처리량(T022)은 기록용이다. 시간 상한(헌법 I)이 걸리면 T022를 줄이되 T014·T016(필수 테스트)은 줄이지 않는다.

## Notes

- **(핵심 로직)** 태스크(T007~T009, T011, T017)는 락·상태 전이·스케줄러를 다룬다. 구현은 세션이 하고 승인 요약에 설명 체크 질문이 붙는다.
- 이 기능은 사용자 화면이 없다. 결과는 상태, 이벤트, 003의 표시로 확인한다.
- 이벤트는 커밋 후 발행이라 서버가 멈추면 유실될 수 있다(허용된 한계). 중복 발행은 금지다.
- 각 태스크 또는 논리적 묶음 후 커밋한다.
