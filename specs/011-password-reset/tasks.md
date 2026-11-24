# Tasks: 비밀번호 재설정

**Input**: [spec.md](spec.md), [plan.md](plan.md), [contracts/password-reset-api.md](contracts/password-reset-api.md)

규칙: 테스트 먼저, 실패 확인 뒤 구현한다. docs/·specs/는 고치지 않는다.

## Phase 1: 백엔드 기반

- [ ] T001 `V4__password_reset.sql`: plan 결정 1의 `password_reset` 표(`email_key` PK, `member_id` NULL·FK, `token_hash` NULL·인덱스, 만료·사용·한도 열), `member.password_changed_at DATETIME(6) NULL`

## Phase 2: US1·US2 백엔드 (P1)

- [ ] T002 [US2] 테스트: 가입한 주소·인증 전·탈퇴·처음 보는 대학 주소의 요청이 모두 같은 202 본문, 메일 이벤트는 가입한 주소만. 비대학 주소 400 `errors[email]`
- [ ] T003 [US2] 테스트: 1분 안 재요청·24시간 6번째가 네 경우 모두 429 + `retryAt`, 한도는 가입 여부와 무관하게 같은 줄로 셈. 재설정 요청이 `login_attempt`를 바꾸지 않음
- [ ] T004 [US2] 테스트: 메일 발송이 요청 스레드 밖(발송이 느리거나 실패해도 202가 바로 옴, 실패는 WARN·가린 주소)
- [ ] T005 [US1] 테스트: 확인 204 → 비밀번호 바뀜, `used_at`, 그 주소 `login_attempt` 삭제(잠긴 회원도 바로 로그인), 커밋 뒤 세션 전부 삭제(다른 세션 401), 쿠키 안 건드림
- [ ] T006 [US1·US2] 테스트: 400 `INVALID_TOKEN`(없음·새 요청으로 무효·탈퇴), 409 `TOKEN_USED`, 410 `TOKEN_EXPIRED`(1시간), 400 `errors[password]`(토큰 그대로). 같은 링크 동시 제출 두 건 → 204 하나·409 하나(500 없음)
- [ ] T006a [US1] 테스트: 재설정 전에 만든 세션은 커밋 뒤 삭제가 실패해도 401(`password_changed_at`). 옛 비밀번호 로그인과 재설정 확인을 동시에 반복(예: 100회)해도 재설정 뒤 쓸 수 있는 옛 비밀번호 세션 0건(로그인의 조건부 세션 INSERT)
- [ ] T007 테스트: 탈퇴하면 그 주소 `password_reset` 삭제(장부 재적용도 같이). 재설정 확인과 탈퇴 동시 → 교착·500 없음
- [ ] T008 테스트: 정리 작업이 24시간 지난 요청 기록을 지움(토큰 유효하면 남김)
- [ ] T009 구현: 요청·확인 API, 비동기 메일, `password_changed_at`과 세션 조회 조건, 로그인의 조건부 세션 INSERT, `RESET_LINK_BASE_URL`, 010 `erase`에 삭제 추가, `SessionCleanup` 정리, 로그에 이메일 없음(plan 결정 1~8). compose에 `RESET_LINK_BASE_URL`(DOMAIN으로)

## Phase 3: 프론트

- [ ] T010 [P] 목(MSW)·타입: 계약 그대로(가입 여부와 무관한 같은 202와 한도, 확인 오류 4종)
- [ ] T011 테스트 → 구현: 로그인 화면 "비밀번호를 잊었어요" 링크, `/password-reset` 요청 화면(202 안내 + 다시 요청 시각, 429 남은 시간, 400 칸 오류)
- [ ] T012 테스트 → 구현: `/password-reset/confirm?token=` 새 비밀번호·확인 입력, 204면 `forget` 뒤 `/login` + "비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.", 토큰 오류 3종은 오류 화면 + 재요청 링크, 비밀번호 오류는 칸 옆
- [ ] T013 e2e: 목 e2e(360·430·1280)와 실서버 e2e(로깅 메일에서 링크를 읽어 재설정 → 새 비밀번호 로그인 → 옛 세션 401)

## Phase 4: 백엔드 검수 반영 (2026-11-25, 05b9180 검수)

- [ ] T014 테스트 → 구현: 정리 조건을 `last_sent_at < now − 24h` 하나로(plan 결정 7 고침). 테스트: 창은 24시간 넘게 지났고 `last_sent_at`은 30초 전인 비회원 줄(토큰 NULL)과 회원 줄(토큰 유효)을 만들고 정리 → 둘 다 남고, 바로 다음 요청은 둘 다 429. 기존 T008 표는 `last_sent_at` 기준으로 맞춤
- [ ] T015 테스트 → 구현: 커밋 뒤 세션 삭제를 `created_at < password_changed_at`인 세션만으로(plan 결정 3-3 고침). 테스트: 변경 시각 뒤에 만든 세션(새 비밀번호 로그인)은 확인 뒤에도 남고 200
- [ ] T016 테스트: 잠긴 회원(실패 5회, `locked_until` 미래)의 재설정 요청이 202, 메일 1건, 그 `login_attempt` 행은 그대로(FR-009)
- [ ] T017 테스트: 같은 링크 동시 제출을 확정적으로 겹치게(회원 행을 잡아 둔 채 두 확인을 보내고 `awaitLockWait` 뒤 풀기) → 204 하나·409 하나. 트랜잭션 안 토큰 재확인을 빼면 실패해야 한다

## 의존 관계

T001 → 백엔드 테스트 → T009 → T014~T017. 프론트 T010~T012는 목으로 병렬, T013 실서버는 백엔드 머지 뒤.
