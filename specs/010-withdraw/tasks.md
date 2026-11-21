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

## Phase 6: 머지 후 재검수 반영 (2026-11-19)

백엔드 (`backend/recheck-fixes`):

- [ ] T020 테스트 → 구현: 미할당 글자(Cn: U+FFF0, U+2065, U+E0002 등)를 `Keys.isInvisible`이 보이지 않는 글자로 본다. "탈퇴한 회원"+U+FFF0은 예약 이름으로, "홍길동"+U+FFF0은 보이지 않는 글자로 거부. 예약 이름 판정은 걸러낸 뒤 NFC를 한 번 더 한다(풀어 쓴 자모 사이 U+034F 우회)
- [ ] T021 테스트 → 구현: 탈퇴 비밀번호가 공백만이면 400 `Bad Request`(`errors[password]`), 실패 횟수 그대로(계약 400 줄)
- [ ] T022 `WithdrawRequest.toString`이 비밀번호를 가린다(`SecretsInToStringTest`에 추가)
- [ ] T023 `deploy/backup.sh` 삭제 패턴이 강제 종료로 남은 `.tmp`도 지운다
- [ ] T024 `NotificationCleanup.run()` 교착(1213) 재시도. 가능하면 교착 원인을 `SHOW ENGINE INNODB STATUS`로 먼저 확인
- [ ] T025 테스트 → 구현: 탈퇴의 설명 비우기 UPDATE가 작은 테이블에서도 organizer 인덱스를 쓴다(전체 행 X 잠금 → 006 체크와 교착 방지). 잠금 범위는 plan 결정 3 그대로
- [ ] T026 테스트 보강: 막는 공구 id 오름차순을 구분하는 데이터(참여자 줄 공구가 먼저 생김), `pending_signup`·`login_attempt` 삭제가 다른 이메일 행을 남김, 동시 탈퇴 테스트는 세션을 미리 넣어 씀
- [ ] T027 `load/k6/browse.js`에 `checks: ['rate==1']` 임계값

프론트 (`frontend/recheck-fixes`):

- [ ] T028 테스트 → 구현: 화면 세션의 회원이 바뀌면(로그인, 다른 탭 로그인 뒤 me 재확인) 이전 회원의 조회 캐시를 지운다. mutation 캐시는 남긴다(VerifyPage)
- [ ] T029 실서버 탈퇴 e2e: 마감을 지난 시각으로 바꾸는 SQL을 제출 직전으로 옮긴다(로컬 마감 스케줄러 30초와 경합)
- [ ] T030 동의문 본문과 탈퇴 영역 문구를 새 `docs/consent-v1.md`와 맞춘다(풀리는 방법, 동의 시각·버전)
- [ ] T031 VerifyPage: `errors[token]`(학교 판정 실패) 400이면 토큰 오류 화면처럼 폼을 닫고 가입 화면 링크. 목 409가 로그인 실패 횟수를 지움(백엔드와 같게). `Consent.test`가 작업 디렉터리와 무관하게 동의문을 읽음

## Phase 7: 복구 장부와 백업 알림 (2026-11-22, plan 결정 5-2·5-3)

- [ ] T032 테스트 → 구현: 탈퇴가 커밋되면 장부 파일에 `memberId`·가입 시각·탈퇴 시각 한 줄. 롤백·409·400이면 쓰지 않음. 쓰기 실패해도 204 + ERROR 로그
- [ ] T033 테스트 → 구현: 기동 때 장부 재적용. id·`created_at`이 같고 탈퇴 전인 회원만 익명화(결정 5 전부, 탈퇴 시각은 장부 값). `created_at`이 다른 같은 번호 회원은 건드리지 않음. 이미 탈퇴한 회원은 그대로. `reanonymized=N` INFO, OPEN 공구가 걸리면 WARN
- [ ] T034 테스트 → 구현: 8일 지난 줄 정리(기동 때와 하루 한 번)
- [ ] T035 `deploy/compose.yaml`: 장부 디렉터리 바인드 마운트와 `WITHDRAW_LEDGER`, 백엔드 docker 로그 회전. `deploy/.env.example`에 `WITHDRAW_LEDGER`·`HC_PING_URL` 빈 칸
- [ ] T036 `deploy/backup.sh`: 성공하면 `HC_PING_URL`, 실패하면 `HC_PING_URL/fail` 호출(비어 있으면 경고만). 실패 경로를 임시 디렉터리에서 확인
- [ ] T037 복구 리허설: 로컬에서 덤프 → 탈퇴 → 덤프 복구 → 재시작 → 그 회원이 다시 익명화됨, 다른 회원은 그대로. 결과를 관제탑에 보고

## 의존 관계

009 백엔드 머지 → T001 → US1 → US2. 프론트 T011~T013은 목으로 병렬, T014는 백엔드 머지 뒤.
