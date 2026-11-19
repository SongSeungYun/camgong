# Tasks: 회원 탈퇴(익명화)

**Input**: [spec.md](spec.md), [plan.md](plan.md), [contracts/withdraw-api.md](contracts/withdraw-api.md)

규칙: 테스트 먼저, 실패 확인 뒤 구현한다. 백엔드는 009 머지 뒤 시작한다(마이그레이션 V3). docs/·specs/는 고치지 않는다.

## Phase 1: 백엔드 기반

- [ ] T001 `V3__withdraw.sql`: `member.display_name_key`·`email`·`email_key` NULL 허용, `withdrawn_at DATETIME(6) NULL` (plan 결정 1)

## Phase 2: US1 탈퇴 (P1)

- [ ] T002 [US1] 테스트: 성공 204 + 쿠키 삭제, 다른 세션 401, 익명값(이름 "탈퇴한 회원", 키·이메일 NULL, 비밀번호 무효, `withdrawn_at`), 알림·같은 이메일의 `pending_signup`·`login_attempt` 삭제, 공구·참여 행과 체크 값 불변
- [ ] T003 [US1] 테스트: 탈퇴 뒤 같은 이메일·비밀번호 로그인은 없는 계정과 같은 401, 같은 이메일·옛 표시 이름으로 재가입 가능
- [ ] T004 [US1] 테스트: 비밀번호 틀림 400 `INVALID_PASSWORD`(아무것도 안 바뀜, 실패 횟수가 **커밋되어** 증가), 5회 뒤 429 `LOGIN_LOCKED`(로그인도 잠김), 비밀번호 누락 400, 비로그인 401, Origin 검사
- [ ] T004a [US1] 테스트: 같은 회원의 탈퇴 두 건 동시 → 하나만 204, 다른 하나 401(500 없음). 로그인과 탈퇴 경쟁 뒤 탈퇴 회원의 세션으로 요청하면 401(`AuthFilter`). 표시 이름 "탈퇴한 회원" 가입 거부. 탈퇴 회원에게 늦게 온 알림 이벤트는 쓰지 않음
- [ ] T005 [US1] 테스트: 체크표·상세·참여자 명단에 "탈퇴한 회원"이 보이고 개설자가 탈퇴 회원 줄을 계속 체크할 수 있음
- [ ] T006 [US1] 구현: `POST /api/auth/withdraw`(비밀번호 확인·횟수는 트랜잭션 밖), `AuthFilter` 탈퇴 회원 거부, 이름 예약, 알림 쓰기에서 탈퇴 회원 제외 (plan 결정 1·3·4·4-1·4-2·5)

## Phase 3: US2 막기와 동시성 (P1)

- [ ] T007 [US2] 테스트: OPEN 개설자·OPEN 참여자·마감 지난 OPEN 참여자 409 `ACTIVE_GROUPBUY` + `blockers`(role, 표시 상태, id 오름차순), COMPLETED·FAILED·CANCELLED만 있으면 성공
- [ ] T008 [US2] 테스트: 탈퇴와 새 참여·새 개설 동시 요청 반복(예: 100회)에서 "탈퇴했는데 OPEN 공구에 참여 행 있음" 0건
- [ ] T009 [US2] 구현: member `FOR UPDATE`(탈퇴) / `FOR SHARE`(새 참여·개설) 잠금, 막는 조건은 일반 SELECT (plan 결정 2·3)
- [ ] T010 [US2] 004 참여 경합 k6 재측정, 결과를 메시지로 관제탑에 보냄

## Phase 4: 프론트

- [ ] T011 [P] 목(MSW)·타입: `/api/auth/withdraw` 계약 그대로(204, 400 두 종류, 409 blockers, 429)
- [ ] T012 테스트 → 구현: 내 활동의 탈퇴 영역과 대화상자(비밀번호, 되돌릴 수 없음 확인, 포커스 트랩·Esc), 오류 표시, 409 blockers 목록과 안내(`PENDING_CLOSE`가 역할보다 우선)·링크, 성공 시 세션 비움 → 목록 + "탈퇴했어요"
- [ ] T013 [P] 테스트 → 구현: 가입(인증) 화면에 동의문 본문 `<details>`("자세히 보기"), 본문은 `docs/consent-v1.md`의 구분선 사이 내용 그대로
- [ ] T014 e2e: 목·실서버 e2e에 탈퇴 시나리오(360·430·1280)

## Phase 5: 재검수 반영 (2026-11-19, plan 결정 5·5-1)

- [ ] T015 [US1] 백엔드 테스트 → 구현: 탈퇴하면 그 회원이 연 모든 공구(상태 무관)의 `description`이 NULL, 그 회원의 `create_guard` 삭제, 다른 회원 공구의 설명은 그대로. 성공 시 `memberId`만 INFO 로그
- [ ] T016 백엔드 테스트 → 구현: 가입 경합으로 UNIQUE 위반이 나도 로그에 이메일·표시 이름이 남지 않음(`PrivacyIT`)
- [ ] T017 배포: `deploy/backup.sh` 7일 지난 덤프 삭제, `deploy/compose.yaml` mysql binlog 끔
- [ ] T018 [P] 프론트 테스트 → 구현: 동의문 본문을 새 `docs/consent-v1.md`와 맞춤(연락처 문장 없음), 탈퇴 영역·대화상자 문구가 지우는 것·남는 것을 동의문과 같게 말함, 개설 폼 설명 칸에 "전화번호·계좌번호는 적지 마세요" 안내, 목(MSW) 탈퇴가 그 회원 공구의 설명을 비움
- [ ] T019 e2e: 실서버 탈퇴 e2e에 "탈퇴한 개설자의 완료 공구 상세에서 설명이 없음" 추가, 전체 실서버 e2e 재실행

## 의존 관계

009 백엔드 머지 → T001 → US1 → US2. 프론트 T011~T013은 목으로 병렬, T014는 백엔드 머지 뒤.
