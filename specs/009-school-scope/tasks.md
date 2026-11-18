# Tasks: 전국 대학 가입과 같은 학교 범위

**Input**: [spec.md](spec.md), [plan.md](plan.md), [contracts/school-api.md](contracts/school-api.md)

규칙: 각 구현 태스크 앞의 테스트 태스크를 먼저 쓰고 실패를 확인한다. 백엔드는 `camgong-backend-3c`, 프론트는 `camgong-e4`. docs/·specs/는 고치지 않는다.

## Phase 1: 백엔드 기반

- [ ] T001 `src/main/resources/db/migration/V2__school.sql`: `member.school`, `group_buy.school` (`VARCHAR(253) NOT NULL`), 엔티티 필드, 테스트 시드 회원에 학교 (plan 결정 2)
- [ ] T002 [P] 테스트: 학교 키 판정 단위 테스트 — `snu.ac.kr`, `cs.snu.ac.kr`→`snu.ac.kr`, 대문자·앞뒤 공백, `ac.kr`·`fakeac.kr`·`stanford.edu`·`gmail.com` 거부, 묶음(`skku.edu,g.skku.edu` → `skku.edu`, `mail.g.skku.edu`), 묶음이 `ac.kr` 규칙보다 먼저, 설정 형식 오류(빈 도메인·중복)는 기동 실패 (plan 결정 1)
- [ ] T003 학교 키 판정과 `camgong.auth.extra-school-domains` 설정 구현, 옛 `allowed-email-domains` 제거(설정 파일·`.env.example` 주석·`EXTRA_SCHOOL_DOMAINS`)

## Phase 2: US1 가입 (P1)

- [ ] T004 [US1] 테스트: signup 거부 400 `errors[email]`, verify 시 학교 저장, verify 시점에 목록에서 빠진 도메인은 400 `errors[token]`, `member` 응답에 `school`(login·verify·me)
- [ ] T005 [US1] SignupService·verify·MemberView 구현

## Phase 3: US2 학교 범위 (P1)

- [ ] T006 [US2] 테스트: 개설 시 `group_buy.school` = 개설자 학교
- [ ] T007 [US2] 테스트: 로그인 목록·검색은 내 학교만(`school` 파라미터 무시), 비로그인 목록은 전국, 비로그인 `school` 필터, 카드에 `school`, 커서가 다른 `school`이면 400, `school` 253자 초과 400
- [ ] T008 [US2] 테스트: 다른 학교 공구의 상세·참여·수량 변경·참여 취소는 없는 공구와 같은 404(본문 비교)
- [ ] T009 [US2] 테스트: `GET /api/schools?q=` — 부분 일치, 대소문자, 최대 10개 사전순, 공구 없는 학교 제외, q 누락·50자 초과 400
- [ ] T010 [US2] 구현: 개설 시 학교 복사, 목록·검색 쿼리와 인덱스(EXPLAIN 확인), 상세·`findHead` 학교 조건, `/api/schools`
- [ ] T011 [US2] 003 목록 k6 측정 재실행, 결과를 메시지로 관제탑에 보냄(관제탑이 `docs/benchmarks/`에 반영)

## Phase 4: 프론트 (US1·US2)

- [ ] T012 [P] 목(MSW)·타입: `member.school`, 카드·상세 `school`, 목록 `school` 파라미터, `/api/schools`, 다른 학교 404, 가입 도메인 규칙(목은 `ac.kr` + 고정 묶음 예시)
- [ ] T013 [P] 테스트 → 구현: 가입 화면 문구("대학 메일(ac.kr 등)"), 서버 `errors[email]` 표시
- [ ] T014 테스트 → 구현: 목록 카드 학교 표시, 비로그인 학교 검색(자동완성, 키보드 선택, 결과 없음 안내, URL 쿼리 `school` 유지·새로고침), 로그인 시 숨기고 "우리 학교 · {school}" 머리말
- [ ] T015 e2e: 목 e2e·실서버 e2e에 학교 시나리오(360·430·1280)

## 의존 관계

T001 → T002/T003 → US1 → US2 백엔드. 프론트 T012~T014는 계약만 보고 목으로 병렬, T015는 백엔드 머지 뒤.
