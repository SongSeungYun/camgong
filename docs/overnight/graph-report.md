# Graph Report - graph-corpus-20261026-053712  (2026-10-26)

## Corpus Check
- Corpus is ~46,184 words - fits in a single context window. You may not need a graph.

## Summary
- 519 nodes · 907 edges · 28 communities (23 shown, 5 thin omitted)
- Extraction: 84% EXTRACTED · 16% INFERRED · 0% AMBIGUOUS · INFERRED: 147 edges (avg confidence: 0.79)
- Token cost: 375,723 input · 0 output

## Community Hubs (Navigation)
- 이벤트 카탈로그와 정합성
- 005 마감 확정 스케줄러
- 공통 결정: 시계·오류 형식·카테고리
- 004 참여 취소와 계약·태스크
- 003 목록·내 활동·표시 상태
- 001 수정·취소와 완료 이벤트 계약
- 004 참여·재시도·게이트 설정
- 001 개설·임시 인증·공개 규칙
- 005 중복 방지와 SKIP LOCKED
- 상태 전이와 도메인 결정
- 불변식과 4방식 동시성 측정
- 006 개설자 체크표
- 헌법 원칙과 완료 기준
- 아키텍처 공통 규약
- 003 N+1 측정 절차
- 교차 점검과 스키마 누적 변경
- 설계 계획 결정 기록과 스파이크
- 001·003 문서 묶음
- 002 가입·인증
- 취소는 상태 전환, 404 통일
- 002·004 문서 묶음
- 003 수치 일관성(FR-015)
- 002 로그인 실패 잠금
- 002 회원 정보 노출
- 003 API·quickstart
- 003 읽기 전용
- 004 research
- 004 1인당 상한 없음

## God Nodes (most connected - your core abstractions)
1. `GroupBuy (공구)` - 13 edges
2. `Notification (앱 안 알림함)` - 13 edges
3. `Constitution IV: core logic Testcontainers tests (MySQL, not H2)` - 12 edges
4. `State transitions (OPEN -> COMPLETED/CANCELLED/FAILED)` - 12 edges
5. `001 FR-016 complete when secured == total, notify all` - 11 edges
6. `Quickstart map of constitution IV mandatory tests (4,13 / 14,15 / 7,8,18)` - 10 edges
7. `005 FR-007 중복 확정·이벤트 금지(멱등)` - 10 edges
8. `005 US1 마감 후 자동 모집 실패 확정` - 10 edges
9. `001 FR-012 update before deadline, extend-only, notify` - 9 edges
10. `001 FR-014 cancel before deadline, notify` - 9 edges

## Surprising Connections (you probably didn't know these)
- `Research 2: secured_quantity column on group_buy row + CHECK` --rationale_for--> `QuantityGate (4 strategies: none/pessimistic/optimistic/conditional)`  [INFERRED]
  specs/001-create-groupbuy/research.md → docs/architecture.md
- `Stage-1 time cap 10h (constitution 1.1.0)` --rationale_for--> `Constitution I: scope cap (NON-NEGOTIABLE)`  [EXTRACTED]
  docs/design-plan.md → constitution.md
- `Plan constitution check I-IV (pass)` --references--> `Constitution I: scope cap (NON-NEGOTIABLE)`  [EXTRACTED]
  specs/001-create-groupbuy/plan.md → constitution.md
- `Constitution I-IV alignment (no violations)` --references--> `Constitution II: prove by measurement`  [EXTRACTED]
  docs/overnight/analyze-003.md → constitution.md
- `Constitution I-IV alignment (no violations)` --references--> `Constitution II: prove by measurement`  [EXTRACTED]
  docs/overnight/analyze-004.md → constitution.md

## Hyperedges (group relationships)
- **Duplicate-create guard mechanism** — docs_domain_create_guard, specs_001_create_groupbuy_research_d3, docs_spikes_s1, docs_spikes_s3, specs_001_create_groupbuy_tasks_t016, specs_001_create_groupbuy_spec_fr_009 [EXTRACTED 1.00]
- **Group buy row serialization across 001/004/005** — docs_architecture_group_buy_row_single_truth, specs_001_create_groupbuy_research_d4, docs_architecture_quantity_gate, docs_design_plan_decision_005_plan, docs_cross_check_x8 [EXTRACTED 1.00]
- **Completion flow: full quantity -> COMPLETED -> GroupBuyCompleted** — specs_001_create_groupbuy_spec_fr_016, docs_domain_state_transitions, docs_domain_event_completed, specs_001_create_groupbuy_tasks_t030, specs_001_create_groupbuy_quickstart_scenario_14 [EXTRACTED 1.00]
- **N+1 측정 흐름 (헌법 II, 003)** — specs_003_browse_groupbuy_research_decision_11, specs_003_browse_groupbuy_research_n_plus_one_scenario_a, specs_003_browse_groupbuy_research_n_plus_one_scenario_b, specs_003_browse_groupbuy_tasks_t020, specs_003_browse_groupbuy_tasks_t027, specs_003_browse_groupbuy_tasks_t030, specs_003_browse_groupbuy_quickstart_n_plus_one_measure [EXTRACTED 0.95]
- **QuantityGate 4방식 비교 (004)** — specs_004_participate_groupbuy_research_quantity_gate, specs_004_participate_groupbuy_research_gate_nolock, specs_004_participate_groupbuy_research_gate_pessimistic, specs_004_participate_groupbuy_research_gate_optimistic, specs_004_participate_groupbuy_research_gate_conditional, specs_004_participate_groupbuy_research_decision_8 [EXTRACTED 0.95]
- **오픈채팅 링크 공개 규칙 (001 FR-017 / 003 FR-011 / I8)** — specs_001_create_groupbuy_contracts_groupbuy_api_openchat_rule_fr_017, specs_003_browse_groupbuy_spec_fr_011, specs_003_browse_groupbuy_data_model_disclosure_rule, specs_003_browse_groupbuy_data_model_invariant_i8, specs_001_create_groupbuy_contracts_groupbuy_api_get_groupbuys_id, specs_003_browse_groupbuy_contracts_browse_api_get_detail [INFERRED 0.85]
- **005 중복 확정 방지 메커니즘** — specs_005_close_groupbuy_research_d1, specs_005_close_groupbuy_tasks_t009, specs_005_close_groupbuy_plan_closer, specs_005_close_groupbuy_spec_fr_007, specs_005_close_groupbuy_tasks_t014 [INFERRED 0.85]
- **004 수량 게이트 4방식** — specs_004_participate_groupbuy_tasks_t007, specs_004_participate_groupbuy_tasks_t016, specs_004_participate_groupbuy_tasks_t017, specs_004_participate_groupbuy_tasks_t018, specs_004_participate_groupbuy_tasks_quantity_gate [EXTRACTED 1.00]
- **이벤트 발행에서 알림 소비 흐름** — specs_004_participate_groupbuy_contracts_participation_api_group_buy_completed, specs_005_close_groupbuy_contracts_events_and_config_groupbuy_failed, specs_007_notify_spec_fr_001, specs_007_notify_spec_notification [EXTRACTED 1.00]

## Communities (28 total, 5 thin omitted)

### Community 0 - "이벤트 카탈로그와 정합성"
Cohesion: 0.08
Nodes (46): Spring events consumed AFTER_COMMIT (outbox if loss matters), X1: GroupBuyCompleted missing in 001 -> fixed, CreateGuard (duplicate-create guard table), GroupBuyCancelled (event), Event catalog, GroupBuyChanged (event), GroupBuyCompleted (event), 004 B5: conditional UPDATE depends on useAffectedRows (spike #7 pending) (+38 more)

### Community 1 - "005 마감 확정 스케줄러"
Cohesion: 0.08
Nodes (39): camgong.scheduler.close.* 설정, GroupBuyFailed 이벤트 (groupBuyId, recipientIds), 불변식 I7/I10 (OPEN에서만 FAILED, 마감·미충족 조건), GroupBuyCloser.closeOne (별도 빈), CloseExpiredScheduler, GroupBuyCloseService.closeExpired, QS 측정 (확정 지연, 대량 처리), QS 시나리오 11~13 경계 경쟁 (+31 more)

### Community 2 - "공통 결정: 시계·오류 형식·카테고리"
Cohesion: 0.08
Nodes (33): Injected java.time.Clock (MutableClock in tests), Error format: RFC 7807 ProblemDetail + errors[], S04 group buy detail screen, S05 group buy create screen, group_buy table (CHECK constraints, version, status), 001 quickstart scenario 1: normal create, 001 quickstart scenario 19: open chat link visibility, 001 quickstart scenario 2: no login 401 (+25 more)

### Community 3 - "004 참여 취소와 계약·태스크"
Cohesion: 0.06
Nodes (26): DELETE /api/groupbuys/{id}/participations/me, T021 ParticipationService.leave (핵심), 004 US3 참여 취소하기, S10 알림 목록 화면, 의존: 001 개설·수정·취소 계약, 의존: 002 인증 계약, 의존: 003 목록·상세 계약, 008 FR-002 비로그인은 목록만, 로그인 후 항상 메인 복귀 (+18 more)

### Community 4 - "003 목록·내 활동·표시 상태"
Cohesion: 0.07
Nodes (35): GET /api/groupbuys 목록, GET /api/me/groupbuys 내 활동, CANCELLED 비노출, COMPLETED 모집 완료, DisplayStatus 표시 상태 매핑, FAILED 모집 실패, OPEN 모집 중 (status=OPEN AND deadline>now), PENDING_CLOSE 마감됨·확정 대기 (+27 more)

### Community 5 - "001 수정·취소와 완료 이벤트 계약"
Cohesion: 0.06
Nodes (33): 004 B1: FR-004 retry vs FR-009 reject-after-completed ambiguity (user decision needed), 409 NOT_MODIFIABLE, GroupBuyCancelled event, GroupBuyChanged event, GroupBuyCompleted event (001 contract), 001 POST /api/groupbuys/{id}/cancel 취소, 001 PUT /api/groupbuys/{id} 수정, GroupBuy 상태 OPEN/COMPLETED/CANCELLED (+25 more)

### Community 6 - "004 참여·재시도·게이트 설정"
Cohesion: 0.09
Nodes (36): ALREADY_JOINED (409), POST /api/groupbuys/{id}/participations, PUT /api/groupbuys/{id}/participations/me, 004 FR-004 참여 재시도 안전(멱등), 004 FR-012 참여·변경·취소는 이벤트 없음, 모집 완료만, 004 FR-014 응답은 본인 정보만, camgong.quantity-gate 설정 (pessimistic|conditional|optimistic|none), GroupBuyCompleted 이벤트 (groupBuyId, recipientIds) (+28 more)

### Community 7 - "001 개설·임시 인증·공개 규칙"
Cohesion: 0.08
Nodes (30): 409 DUPLICATE_REQUEST, 001 GET /api/groupbuys/{id} 최소 상세, 001 FR-017 openChatUrl 노출 규칙, 001 POST /api/groupbuys 개설, X-Member-Id 임시 인증 헤더, 002 FR-007 로그인 유지 14일 슬라이딩, 002 FR-008 로그인 필요 기능 거부, 공구 목록은 공개, 002 FR-009 로그인 후 메인(목록)으로 복귀 (+22 more)

### Community 8 - "005 중복 방지와 SKIP LOCKED"
Cohesion: 0.10
Nodes (26): QS 시나리오 8~10 중복 실행 방지, 005 FR-007 중복 확정·이벤트 금지(멱등), 005 SC-002 동시 실행 1,000회 중복 0건, 005 US2 동시 실행에도 한 번만 확정, T008 후보·SKIP LOCKED 선점 쿼리 (핵심), T014 CloseConcurrencyIT, GroupBuyCancelled/취소 이벤트 (001 발행), GroupBuyUpdated/수정 이벤트 (001 발행) (+18 more)

### Community 9 - "상태 전이와 도메인 결정"
Cohesion: 0.15
Nodes (14): PENDING_CLOSE (display status: past deadline, unconfirmed), X3: status field name unified (status, not state), COMPLETED (recruitment done), GroupBuyFailed (event), FAILED (deadline missed), GroupBuy (aggregate root), Invariants I1-I10, Member (+6 more)

### Community 10 - "불변식과 4방식 동시성 측정"
Cohesion: 0.13
Nodes (16): I1 secured_quantity = Σ participation.quantity, I2 1 ≤ secured ≤ total (게이트+CHECK), 004 4방식 k6 측정(last-seat, mixed), 004 quickstart 시나리오 16 마지막 자리 100명 동시, 004 quickstart 시나리오 19 개설자 취소와 참여 경쟁, ConditionalUpdateQuantityGate 단일 UPDATE, NoLockQuantityGate (벤치마크 전용), OptimisticQuantityGate @Version 재시도 3회 (+8 more)

### Community 11 - "006 개설자 체크표"
Cohesion: 0.13
Nodes (18): Participation (참여), COMPLETED (체크표 사용 조건), 006 FR-001 체크표는 모집 완료 공구의 개설자만, 006 FR-002 참여자별 이름·수량·금액·상태 및 요약, 006 FR-003 입금 확인 지정/되돌리기 + 시각, 006 FR-004 수령 확인 (입금과 독립), 006 FR-005 목표 상태 지정(멱등), 동시 요청 일관, 006 FR-006 체크 대상은 개설자 아닌 해당 공구 참여자 (+10 more)

### Community 12 - "헌법 원칙과 완료 기준"
Cohesion: 0.17
Nodes (9): Design completion criteria (start implementation when all met), 003 A4: 003 replaces 001 minimal detail GET, Constitution I-IV alignment (no violations), 003 FR coverage (18/18 tasks), Constitution I-IV alignment (no violations), 004 FR coverage (14/14 implementable), Constitution I-IV alignment (no violations), 005 FR coverage (12/12 implementable) (+1 more)

### Community 13 - "아키텍처 공통 규약"
Cohesion: 0.23
Nodes (3): QuantityGate (4 strategies: none/pessimistic/optimistic/conditional), Tech point: concurrency (4 lock strategies, k6), Plan constitution check I-IV (pass)

### Community 14 - "003 N+1 측정 절차"
Cohesion: 0.35
Nodes (6): 003 N+1 측정 표(헌법 II), N+1 시나리오 A 내가 참여한 공구 한 페이지, 최종 ≤2 쿼리, N+1 시나리오 B 상세 개설자용 명단, 최종 ≤4 쿼리, 003 T020 순진한 구현 N+1 기준선 기록, 003 T027 ② fetch join 개선, 003 T030 쿼리 수 상한 단언 QueryCountIT

### Community 15 - "교차 점검과 스키마 누적 변경"
Cohesion: 0.24
Nodes (8): schema.sql cumulative changes table (sec 9), X10: SKIP LOCKED worst-case ~61s delay accepted, X5: schema cumulative table added, X8: lock serialization across 001/004/005 verified, 003 A1: CREATE INDEX IF NOT EXISTS unsupported -> use KEY clause in CREATE TABLE, 005 C1: index syntax same issue as 003 A1, 005 C6: SKIP LOCKED worst case ~61s, 001 T005 schema.sql CREATE TABLE IF NOT EXISTS

### Community 16 - "설계 계획 결정 기록과 스파이크"
Cohesion: 0.24
Nodes (6): Design-plan decision log, Spike 12: FOR UPDATE SKIP LOCKED skips locked rows, Spike 13: keyset pagination and LIKE fast at 10k rows, Feature map 001-007 (create, auth, browse, participate, close, check, notify), Tech point: N+1 reduction, Tech point: scheduler duplicate-run prevention (SKIP LOCKED)

### Community 17 - "001·003 문서 묶음"
Cohesion: 0.25
Nodes (8): 001 checklist requirements, 001 API contract groupbuy-api, 003 checklist requirements, 003 data-model, 003 plan, 003 research, 003 spec 공구 목록·상세 조회, 003 tasks

### Community 18 - "002 가입·인증"
Cohesion: 0.29
Nodes (7): 002 FR-001 이메일·비밀번호 가입, 인증 후 가입 완료·자동 로그인, 002 FR-003 표시 이름 2~20자, 중복 불가, 002 FR-004 학교 이메일 도메인 한정, 인증 링크 24h 1회, 002 FR-005 인증 메일 재발송 1분 간격, Member 회원 (002), 002 US1 학교 이메일 가입·인증 (P1), 002 US3 학교 이메일 한정·인증 메일 재발송 (P2)

### Community 19 - "취소는 상태 전환, 404 통일"
Cohesion: 0.47
Nodes (3): X2: cancelled group -> 404 everywhere, CANCELLED, 001 FR-014 cancel before deadline, notify

### Community 20 - "002·004 문서 묶음"
Cohesion: 0.33
Nodes (6): 002 checklist requirements, 002 spec 회원 가입·로그인·로그아웃, 004 data-model, 004 plan, 004 quickstart, 004 spec 공구 참여·수량 변경·참여 취소

### Community 21 - "003 수치 일관성(FR-015)"
Cohesion: 0.67
Nodes (4): 003 A2: FR-015 numbers-match has no automated test, 003 quickstart 시나리오 21 목록·상세·내 활동 수치 일관, 003 FR-015 목록·상세·내 활동 수치 일치, 003 SC-006 목록·상세 수치 100% 일치

### Community 22 - "002 로그인 실패 잠금"
Cohesion: 0.67
Nodes (3): 002 FR-011 5회 실패 후 15분 잠금, 002 SC-006 5회 실패 후 15분 100% 거부, 002 US4 로그인 남용 방지 (P3)

## Ambiguous Edges - Review These
- `004 US1 공구에 참여하기 (P1)` → `004 FR-010 개설자는 참여·변경·취소 불가`  [AMBIGUOUS]
  specs/004-participate-groupbuy/spec.md · relation: conceptually_related_to

## Knowledge Gaps
- **88 isolated node(s):** `Feature map 001-007 (create, auth, browse, participate, close, check, notify)`, `Member`, `001 SC-001 create within 1 min`, `001 SC-005 3-5 acquaintances succeed unaided`, `003 A4: 003 replaces 001 minimal detail GET` (+83 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 103 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `004 B1: FR-004 retry vs FR-009 reject-after-completed ambiguity (user decision needed)` connect `001 수정·취소와 완료 이벤트 계약` to `헌법 원칙과 완료 기준`?**
  _High betweenness centrality (0.188) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `GroupBuy (공구)` (e.g. with `COMPLETED (체크표 사용 조건)` and `Notification (앱 안 알림함)`) actually correct?**
  _`GroupBuy (공구)` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Feature map 001-007 (create, auth, browse, participate, close, check, notify)`, `Member`, `001 SC-001 create within 1 min` to the rest of the system?**
  _88 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `이벤트 카탈로그와 정합성` be split into smaller, more focused modules?**
  _Cohesion score 0.07896575821104122 - nodes in this community are weakly interconnected._
- **Are the 13 inferred relationships involving `Notification (앱 안 알림함)` (e.g. with `007 FR-001 모집완료/실패/수정/취소 알림 대상 규칙` and `007 FR-002 대상은 사건 시점의 참여 기록 보유자`) actually correct?**
  _`Notification (앱 안 알림함)` has 13 INFERRED edges - model-reasoned connections that need verification._
- **Should `005 마감 확정 스케줄러` be split into smaller, more focused modules?**
  _Cohesion score 0.08456659619450317 - nodes in this community are weakly interconnected._

### Low-confidence Hints
_AMBIGUOUS edges — the extractor was unsure. Verify before acting on these._

- **What is the exact relationship between `004 US1 공구에 참여하기 (P1)` and `004 FR-010 개설자는 참여·변경·취소 불가`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._