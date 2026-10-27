---

description: "모바일 우선 웹 화면(PWA) 구현 태스크"
---

# Tasks: 모바일 우선 웹 화면

**Input**: `/specs/008-frontend-web/` 의 plan.md, spec.md, research.md, data-model.md, contracts/ui-api-map.md, quickstart.md. 서버 계약은 `specs/001~007/contracts/`가 기준(읽기 전용).

**Prerequisites**: plan.md, spec.md. 백엔드 없이 진행하므로 목(MSW)으로 계약 응답을 구현한다. 실제 백엔드가 있는 기능은 같은 시나리오를 목 없이 다시 돌린다(quickstart "실제 백엔드 재확인").

**Tests**: research.md 결정 9에 따라 화면 단위 테스트(Vitest + Testing Library + MSW)를 각 스토리에 포함하고, Playwright(360px)는 핵심 시나리오만 둔다. 각 스토리에서 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.

**Organization**: 사용자 스토리별로 묶었다. 구현은 작업 세션(AI)이 태스크 단위로 하고, 사용자가 승인한 뒤 커밋한다(`docs/sessions/README.md`). 이 기능에는 `(핵심 로직)` 태스크가 없다(락·쿼리·상태 전이가 서버에 있다). 승인 요약에는 diff, 테스트 결과, 계약과의 차이만 적는다.

**소유 범위**: 프론트 세션은 `frontend/`만 고친다. `specs/`·`docs/`는 읽기만 하고, 계약·spec이 모호하거나 충돌하면 멈추고 사용자에게 알린다. 서버 계약 변경이 필요하면 관제탑에 요청한다.

**축소 순서(헌법 I, 일정이 막힐 때)**: US7(알림함) → US6(체크표) → US5(내 활동) 순으로 통째로 뺄 수 있게 이 세 스토리는 서로 독립이다. US1~US4는 줄이지 않는다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 태스크에 의존하지 않아 병렬 가능
- **[Story]**: US1(목록), US2(로그인·상세), US3(개설·수정·취소), US4(참여), US5(내 활동), US6(체크표), US7(알림함)
- 경로는 저장소 루트 기준(`frontend/…`). 각 태스크 끝의 `근거:`는 spec FR/시나리오와 `contracts/ui-api-map.md` 절이다.
- 공통 규칙: 서버 응답의 문자열은 JSX 텍스트로만 렌더링하고 `dangerouslySetInnerHTML`을 쓰지 않는다(FR-010). 새 의존성은 research.md에 이유가 있는 것만 쓴다.

## Phase 1: Setup

**Purpose**: 프로젝트 뼈대, 개발·테스트 도구, PWA 기본

- [ ] T001 `frontend/`에 Vite + React 19 + TypeScript(strict) 프로젝트 생성: `package.json`, `tsconfig.json`, `vite.config.ts`, `index.html`, `src/main.tsx`. 의존성: react-router, @tanstack/react-query, react-hook-form. 개발 의존성: vitest, @testing-library/react, @testing-library/user-event, jsdom, msw, @playwright/test, vite-plugin-pwa, eslint. 근거: plan.md Technical Context, research.md 결정 2~9
- [ ] T002 [P] `frontend/vite.config.ts` 개발 서버 프록시 `/api` → `http://localhost:8080`(값은 `VITE_API_TARGET` 환경변수, 기본값 포함)으로 같은 출처를 만든다. `VITE_USE_MOCK`·`VITE_API_TARGET` 외 환경 값은 쓰지 않는다. 시크릿 없음. 근거: research.md 결정 10·13
- [ ] T003 [P] `frontend/vitest.config.ts`와 `frontend/src/test/setup.ts`: jsdom 환경, Testing Library 정리, MSW 서버 시작·리셋·종료, 각 테스트 전 쿼리 캐시와 세션 초기화. `package.json`에 `test`, `e2e`, `lint`, `typecheck` 스크립트. 근거: research.md 결정 9
- [ ] T004 [P] `frontend/playwright.config.ts`: 뷰포트 360×800 기본 + 1280×800 프로젝트 하나, 목 모드(`VITE_USE_MOCK=true`)로 개발 서버를 띄워 실행. 근거: quickstart.md 준비, SC-003
- [ ] T005 [P] PWA 기본: `frontend/vite.config.ts`에 `vite-plugin-pwa`(자동 업데이트, **오프라인 캐시 없음**: precache·runtime 캐시 비활성), `frontend/public/` 아이콘 192·512 자리표시, 매니페스트(이름 "캠공", `display: standalone`, `start_url: /`, `theme_color`). 근거: research.md 결정 14, spec 가정(PWA), quickstart 36
- [ ] T006 [P] `frontend/README.md`(실행·테스트·목/실제 백엔드 전환 방법 한 장)와 `frontend/.gitignore`(node_modules, dist). 근거: plan.md

---

## Phase 2: Foundational (모든 스토리의 선행 조건)

**Purpose**: 계약 타입, API 클라이언트, 공통 UI, 시각·오류 유틸, 인증 가드, 목 서버 뼈대

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 스토리 작업을 시작하지 않는다.

- [ ] T007 [P] `frontend/src/shared/lib/time.ts`와 `time.test.ts`: `formatKst(iso)`(`Intl.DateTimeFormat`, `timeZone: 'Asia/Seoul'`, 기기 시간대 무관), `toIsoWithKstOffset(localDateTimeValue)`(`datetime-local` 값을 KST로 해석해 `+09:00` 오프셋 ISO 문자열), `remainingText(deadlineIso, nowMs)`(불러온 시점 기준 남은 시간). 기기 시간대를 UTC로 바꾼 테스트 포함. 근거: FR-004, SC-009, research.md 결정 11
- [ ] T008 [P] `frontend/src/shared/lib/money.ts`: 원 단위 포맷(`1,200원`), `amount(unitPrice, quantity)`. 근거: FR-020, FR-024
- [ ] T009 [P] `frontend/src/shared/lib/safeLink.ts`와 테스트: `https:` 주소만 링크로 허용(`new URL` 파싱, 그 외 `null`), `rel="noopener noreferrer"`·새 창 속성 제공 컴포넌트용 헬퍼. `http:`·`javascript:`는 `null`. 근거: FR-009, 시나리오 33
- [ ] T010 `frontend/src/shared/api/types.ts`: 계약에서 옮겨 적은 타입 — 카드, 상세(`status`, `isOrganizer`, `myParticipation`, `openChatUrl`, `participants`, `participantCount`, 006 확장 `paidConfirmedAt`·`receivedConfirmedAt`), 목록 응답(`items`, `nextCursor`), 체크표(`summary`, `rows`), 알림, 세션 `{id, displayName}`. 상태·카테고리·알림 유형은 문자열 유니온 + 알 수 없는 값 가드(`isKnownStatus`). 카테고리 표시 이름 매핑: FOOD 식품, DAILY 생활용품, KITCHEN 주방, HEALTH_BEAUTY 건강뷰티, STATIONERY 문구학용품, ETC 기타. 근거: data-model.md 3, FR-008
- [ ] T011 `frontend/src/shared/api/client.ts`와 테스트: `fetch` 래퍼 — `credentials: 'include'`, JSON 본문(`application/json`), 오류 본문(`ProblemDetail` + `errors[]`)을 `ApiError{status, code(title), reason, fieldErrors, 추가 속성}`로 변환, 5xx·네트워크 오류는 일반 오류로 변환(서버 `detail` 비노출), 401이면 `onUnauthenticated` 콜백 호출. `X-Member-Id`는 보내지 않는다. CSRF 헤더 없음(002 계약). 근거: contracts/ui-api-map.md 2·3, data-model.md 6, FR-006
- [ ] T012 `frontend/src/shared/lib/errorMessage.ts`와 테스트: `ui-api-map.md` 3절 문구표를 코드 → 문구 함수로 옮긴다(`NOT_JOINABLE`의 `reason` 3종 구분, `PRICE_CHANGED`·`INSUFFICIENT_QUANTITY`의 추가 속성 반영, 429 `retryAt`, 알 수 없는 코드·상태는 일반 문구). 표의 모든 행에 테스트 한 줄씩. 근거: FR-006, FR-028, SC-007
- [ ] T013 [P] `frontend/src/shared/ui/`: 모바일 우선 기본 컴포넌트와 CSS Modules + CSS 변수(색·간격·글자 토큰) — `Button`(처리 중 비활성화), `TextField`/`SelectField`/`TextAreaField`(레이블·오류 표시, 필드 옆 오류), `Badge`(상태 뱃지), `ProgressBar`, `StateView`(`loading`/`empty`/`error`+다시 시도/`ready` 구분), `ConfirmDialog`, `Layout`(읽기 폭 제한 중앙 정렬, 터치 대상 최소 44px). 근거: FR-001, FR-007, FR-026, data-model.md 1, research.md 결정 7
- [ ] T014 `frontend/src/features/auth/session.tsx`와 테스트: 세션 컨텍스트(`checking`/`anonymous`/`authenticated`, `member{id, displayName}`), 시작 시 `GET /api/auth/me`, `logout()`, 401 콜백에서 세션을 `anonymous`로 만들고 `queryClient.clear()`. 근거: data-model.md 2, FR-003, research.md 결정 4·10
- [ ] T015 `frontend/src/app/router.tsx`, `frontend/src/app/RequireAuth.tsx`, `frontend/src/app/App.tsx`: 라우트 표(`ui-api-map.md` 1절 주소)를 지연 로딩으로 정의, `RequireAuth`는 `checking` 동안 깜빡임 없이 확인 중 표시, `anonymous`면 `/login`으로 보내고 로그인 후에는 항상 `/`로 간다. 존재하지 않는 라우트는 찾을 수 없음 화면. 최상위 에러 경계. 근거: FR-002, FR-003, 시나리오 7
- [ ] T016 [P] `frontend/src/app/Header.tsx`: 로그인 화면 상단 헤더 자리(로고→`/`, 내 활동 링크, 로그아웃). 알림 배지 자리는 US7에서 채운다. 근거: FR-018, FR-035 자리
- [ ] T017 `frontend/src/mocks/`: MSW 뼈대 — `server.ts`(Vitest), `browser.ts`(개발), `handlers/index.ts`, 인증 핸들러(`/api/auth/*`: me/login/logout/signup/resend/verify, 쿠키 세션 흉내 + 시나리오 토글: 만료 401, 잠금 429), `data/seed.ts`(공구 25개 이상, 상태 4종·카테고리 6종·마감 임박 포함, 회원 3명 역할). `src/main.tsx`는 `VITE_USE_MOCK=true`일 때만 브라우저 목을 시작. 목 응답은 `ui-api-map.md`·계약과 같은 모양이어야 한다. 근거: research.md 결정 8, plan.md Complexity Tracking

**Checkpoint**: `npm test`와 `npm run typecheck`가 통과하고, 목 모드에서 빈 앱이 라우트별로 열린다.

---

## Phase 3: User Story 1 - 목록에서 공구 찾기 (Priority: P1) 🎯 MVP

**Goal**: 비로그인 상태에서 목록(S03)을 보고 정렬·필터·검색·더 보기를 쓴다.

**Independent Test**: 로그인 없이 360px에서 `/`를 열어 quickstart 1~6, 35(목록)를 확인한다.

### Tests for User Story 1

- [ ] T018 [P] [US1] `frontend/src/features/groupbuy/list/GroupBuyList.test.tsx`: 카드 필드 전부 표시(시나리오 1), 더 보기로 중복 없이 이어지고 `nextCursor: null`이면 버튼 사라짐(2), 조건 변경 시 주소 쿼리 반영 + 처음부터(3), 빈 목록과 검색 결과 없음 문구 구분(4), `PENDING_CLOSE` 뱃지(5), 알 수 없는 `status`는 일반 표시(6), 로딩·오류(다시 시도)·빈 상태 구분(FR-007). 근거: US1, FR-008, FR-011~015
- [ ] T019 [P] [US1] `frontend/src/features/groupbuy/list/listParams.test.ts`: 주소 쿼리 ↔ 조건 변환 — 기본값(`status=OPEN`, `sort=latest`), 알 수 없는 값은 기본값으로 되돌림, `q` 앞뒤 공백 제거·공백뿐이면 검색 안 함·50자 제한, 커서는 주소에 넣지 않음. 근거: data-model.md 4, FR-012, FR-013, FR-015

### Implementation for User Story 1

- [ ] T020 [P] [US1] `frontend/src/mocks/handlers/groupbuys.ts`의 `GET /api/groupbuys`: `status`·`category`·`q`(공백으로 나눈 단어 모두 제목 또는 상품명에 포함)·`sort`·`cursor`·`size`(기본 20) 처리, 불투명 커서, 400 케이스(잘못된 값). 응답은 `browse-api.md` 모양. 근거: ui-api-map.md 1, 003 계약
- [ ] T021 [P] [US1] `frontend/src/features/groupbuy/list/listParams.ts`: 주소 쿼리 파싱·직렬화 훅(`useListParams`), 기본값 복귀, `q` 디바운스 후 주소 반영. 근거: data-model.md 4, research.md 결정 12
- [ ] T022 [US1] `frontend/src/features/groupbuy/api.ts`와 `list/useGroupBuyList.ts`: `useInfiniteQuery`(키 `['groupbuys', 조건]`, `getNextPageParam = nextCursor`), 조건이 바뀌면 커서를 버림. 근거: research.md 결정 4, FR-013
- [ ] T023 [P] [US1] `frontend/src/features/groupbuy/list/GroupBuyCard.tsx`: 제목, 상품명, 개당 가격, 진행률 바(확보/총), 마감 시각(KST)과 남은 시간, 상태 뱃지, 카테고리 이름. 카드 전체가 상세 링크. 근거: FR-011, FR-008
- [ ] T024 [US1] `frontend/src/features/groupbuy/list/ListFilters.tsx`: 정렬(최신·마감 임박), 상태 필터(기본 모집 중), 카테고리 6개, 검색 입력(최대 50자). 선택한 조건이 화면에 표시된다. 근거: FR-012, 시나리오 3
- [ ] T025 [US1] `frontend/src/pages/ListPage.tsx`: 필터 + 카드 목록 + 더 보기 + `StateView`(빈 목록 / 검색 결과 없음 문구 구분: `q` 유무로, 로딩, 오류+다시 시도). 비로그인도 열린다. 헤더에는 로그인/가입 링크(비로그인) 또는 내 활동·로그아웃. 근거: FR-014, FR-007, 시나리오 4
- [ ] T026 [US1] `frontend/tests/e2e/list.spec.ts`: 360px에서 목록 열기, 카테고리·검색으로 조건 변경, 새로고침 후 조건 유지(주소), 가로 스크롤 없음 확인(`document.documentElement.scrollWidth <= innerWidth`). 근거: SC-003, FR-015, 시나리오 3·35

**Checkpoint**: US1 단독으로 동작·테스트 가능(MVP).

---

## Phase 4: User Story 2 - 로그인하고 상세 보기, 가입하기 (Priority: P1)

**Goal**: 가입·인증·로그인·로그아웃, 보호 라우트, 상세 화면(S04)의 역할별 표시.

**Independent Test**: quickstart 7~13.

### Tests for User Story 2

- [ ] T027 [P] [US2] `frontend/src/features/auth/auth.test.tsx`: 비로그인으로 보호 라우트 접근 → `/login`, 로그인 후 항상 `/`(7); 가입 제출 → 인증 메일 안내와 재발송(이른 재발송 429 시 대기 안내)(8); 인증 링크 성공/만료/이미 사용/잘못된 토큰(9); 이름 충돌(409 `DISPLAY_NAME_TAKEN`) 시 이름 입력란 노출; 로그인 실패는 어느 쪽인지 알리지 않음, 5회 실패 잠금 안내, 로그인 화면의 인증 메일 재발송 고정 안내(인증 전 계정도 401 `INVALID_CREDENTIALS`, 10); 로그아웃 후 캐시 비움·뒤로 가기에서 이전 내용 없음(13); 폼 작성 중 401 → 로그인 + 소실 안내(31). 비밀번호 `type=password`, 어디에도 저장하지 않음. 근거: US2, FR-002, FR-003, FR-016~019
- [ ] T028 [P] [US2] `frontend/src/features/groupbuy/detail/GroupBuyDetail.test.tsx`: 개설자/참여자/비참여자 세 역할의 필드와 버튼 노출(`data-model.md` 3 노출 규칙 표를 한 행씩), 내 참여 수량·금액 vs '참여 안 함'(5), 없는/취소된 공구는 찾을 수 없음 + 목록으로(6, 12), `openChatUrl`·`participants`는 서버 값이 있을 때만(11), 외부 링크는 새 창·https만(33), 제목의 HTML이 문자 그대로 표시(34). 근거: US2-5·6, FR-020~023, FR-009, FR-010

### Implementation for User Story 2

- [ ] T029 [P] [US2] `frontend/src/mocks/handlers/groupbuys.ts`에 `GET /api/groupbuys/{id}`: 로그인 필수(401), 역할별 응답(개설자: `isOrganizer`, `participants`, 모집 완료 시 `openChatUrl`; 참여자: `myParticipation`, 완료 시 `openChatUrl`; 비참여자: 둘 다 `null`), 취소/없음 404. 근거: 003 계약 상세, ui-api-map.md 1
- [ ] T030 [P] [US2] `frontend/src/features/auth/api.ts`와 `useAuth.ts`: signup/resend/verify/login/logout 호출과 변이 훅, 오류를 `ApiError`로 전달. 로그인 성공 시 세션 갱신 후 `/`로 이동. 근거: ui-api-map.md 1, 002 계약
- [ ] T031 [US2] `frontend/src/pages/LoginPage.tsx`: 이메일·비밀번호 폼, 실패 문구(`INVALID_CREDENTIALS`), 잠금(429 `retryAt` 표시), "아직 인증 전이라면 인증 메일 다시 받기" 고정 안내 + 재발송 진입(`EMAIL_NOT_VERIFIED`는 없다), 가입 링크. 이미 로그인 상태면 `/`로. 근거: FR-018, 시나리오 10
- [ ] T032 [US2] `frontend/src/pages/SignupPage.tsx`: 이메일, 표시 이름(2~20자), 비밀번호(8자 이상·72바이트 이하 안내), 개인정보 수집·이용 동의(`consent`, `consentVersion`) 폼. 규칙은 안내용이고 최종 판단은 서버(필드 오류를 필드 옆에). 접수 후 "인증 메일을 보냈다" 안내 + 재발송 버튼(`resendAvailableAt` 이전 비활성/대기 안내). 근거: FR-016, FR-019, 시나리오 8
- [ ] T033 [US2] `frontend/src/pages/VerifyPage.tsx`(`/verify?token=…`): 링크를 열면 확인 버튼이 있는 화면이 나오고, 사용자의 확인 동작으로 `POST /api/auth/verify`(링크만 열어도 소진되지 않게). 성공 → 로그인 상태로 `/`, 만료/이미 사용/잘못된 토큰 → 이유 + 재발송, `DISPLAY_NAME_TAKEN` → 새 표시 이름 입력 후 재확인. 근거: FR-017, 002 계약 verify, 시나리오 9
- [ ] T034 [P] [US2] `frontend/src/features/groupbuy/detail/useGroupBuyDetail.ts`: `useQuery(['groupbuy', id])`, 404/401 처리. 근거: FR-003, FR-023
- [ ] T035 [US2] `frontend/src/features/groupbuy/detail/GroupBuyDetail.tsx`와 `pages/DetailPage.tsx`: 제목, 설명, 상품명, 카테고리, 구매처 링크(`safeLink`), 개당 가격, 총·확보·남은 수량, 마감(KST), 상태 뱃지, 개설자 이름, 참여 인원 수, 내 참여 수량·금액 또는 '참여 안 함', 서버가 준 경우에만 오픈채팅 링크·참여자 명단. 버튼 영역은 US3·US4·US6이 채우는 슬롯으로 `data-model.md` 3의 노출 규칙을 함수 `visibleActions(detail)`로 구현. 근거: FR-020~023, FR-021
- [ ] T036 [US2] `frontend/tests/e2e/auth.spec.ts`: 보호 라우트 → 로그인 → `/`, 가입 → 인증 → 로그인된 목록, 인증 만료 리다이렉트. 근거: SC-005, 시나리오 7·9·31

**Checkpoint**: US1 + US2가 독립적으로 동작.

---

## Phase 5: User Story 3 - 공구 열기·수정·취소 (Priority: P2)

**Goal**: 개설(S05)·수정(S06)·취소, 필드별 서버 오류 표시.

**Independent Test**: quickstart 14~20.

### Tests for User Story 3

- [ ] T037 [P] [US3] `frontend/src/features/groupbuy/form/GroupBuyForm.test.tsx`: 미리보기 부담 금액(1,200 × 9 = 10,800원)(14), 마감 입력(KST)이 `+09:00` 오프셋 ISO로 전송(기기 시간대 UTC에서도)(15), 서버 400 `errors[]` 여러 개가 해당 필드 옆에 한 번에 표시·입력값 유지·매핑 안 되는 필드는 폼 상단(16), 성공 시 새 상세로 이동(17), 수정 폼에 현재 값 채움 + 마감 연장만/총 수량 ≥ 확보 안내(18), 취소는 확인 대화상자 후 실행(19), 비개설자에게 수정·취소 버튼 없음(20), 처리 중 중복 제출 방지. 근거: US3, FR-004~005, FR-024~026
- [ ] T038 [P] [US3] `frontend/src/features/groupbuy/form/fieldMap.test.ts`: 폼 필드 이름이 계약 요청 본문 필드와 1:1(`title, description, category, itemName, itemUrl, unitPrice, totalQuantity, organizerQuantity, deadline, openChatUrl`)이고 서버 `errors[].field`가 모두 폼 필드에 매핑되거나 폼 상단으로 모이는지. 근거: FR-005, SC-004, data-model.md 5

### Implementation for User Story 3

- [ ] T039 [P] [US3] `frontend/src/mocks/handlers/groupbuys.ts`에 `POST /api/groupbuys`(201 + `Location`; 400 `errors[]`: 제목 100자 초과, 개당 가격 1~1,000,000 밖, 총 수량 2~1,000 밖, 내 수량 ≥ 총 수량, 마감 30일 초과·과거, 구매처·오픈채팅 https 아님; 409 `DUPLICATE_REQUEST`), `PUT /api/groupbuys/{id}`(마감 단축 400 `deadline`, 총 수량 < 확보 400, 비개설자 403, 409 `NOT_MODIFIABLE`+`reason`), `POST /api/groupbuys/{id}/cancel`(204/200, 403, 409). 근거: 001 계약
- [ ] T040 [US3] `frontend/src/features/groupbuy/form/GroupBuyForm.tsx` + `useCreateGroupBuy.ts`/`useUpdateGroupBuy.ts`/`useCancelGroupBuy.ts`: React Hook Form, 필드 이름은 계약과 같게, 안내용 범위(제목 100자, 개당 가격 1~1,000,000, 총 수량 2~1,000, 내 수량 1 이상 총 수량 미만, 마감 지금부터 30일 이내, 구매처·오픈채팅 https), 서버 `fieldErrors`를 `setError`로 필드에 매핑·나머지는 폼 상단, 마감은 `toIsoWithKstOffset`, 내 부담 금액 미리보기, 카테고리 6개 필수 하나, 성공 시 관련 쿼리 무효화(`ui-api-map.md` 5절). 근거: FR-005, FR-024, FR-025, FR-030
- [ ] T041 [US3] `frontend/src/pages/CreatePage.tsx`(`/groupbuys/new`)와 목록 헤더의 "공구 열기" 진입. 성공 → 새 상세. 근거: 시나리오 17
- [ ] T042 [US3] `frontend/src/pages/EditPage.tsx`(`/groupbuys/:id/edit`): 현재 값 채움, 마감 연장만·총 수량 ≥ 확보 안내, 수정 성공 → 상세. 개설자가 아니면 거부 안내. 근거: FR-025, 시나리오 18
- [ ] T043 [US3] `frontend/src/features/groupbuy/detail/OrganizerActions.tsx`(상세의 개설자 영역): 수정 버튼(→ `/edit`), 취소 버튼 + `ConfirmDialog`(확인 없이 취소 안 됨), 취소 성공 → 목록 + 공구가 사라졌다는 안내, `NOT_MODIFIABLE`+`reason` 문구. `visibleActions`(T035)에 연결. 근거: FR-021, FR-026, 시나리오 19~20
- [ ] T044 [US3] `frontend/tests/e2e/create.spec.ts`: 틀린 값 여러 개로 개설 → 필드별 오류 위치 확인 → 올바른 값으로 개설 → 새 상세로 이동. 근거: SC-004, 시나리오 16~17

**Checkpoint**: US1~US3 독립 동작.

---

## Phase 6: User Story 4 - 공구에 참여하고 취소하기 (Priority: P2)

**Goal**: 참여(S07), 수량 변경, 참여 취소, 거부 사유별 안내, 연타 안전.

**Independent Test**: quickstart 21~27.

### Tests for User Story 4

- [ ] T045 [P] [US4] `frontend/src/features/participation/Participation.test.tsx`: 남은 수량 이하만 제출 가능 + 금액 미리보기(21), `PRICE_CHANGED` 시 새 가격 안내·재확인해야 제출(22), `INSUFFICIENT_QUANTITY` 시 최신 남은 수량 안내 + 상세 갱신(23), `NOT_JOINABLE`의 `reason` 3종이 각각 다른 문구이고 상세를 다시 불러옴(24), 참여한 회원에게 수량 변경·참여 취소만 보임·취소는 확인(25), 제출 연타해도 요청 1건·오류처럼 안 보임(`200` 재시도도 성공)(26), 모집 완료 시 뱃지와 참여자에게만 오픈채팅 링크(27), 개설자 거부 문구(`ORGANIZER_NOT_ALLOWED`). 근거: US4, FR-027~030, SC-007
- [ ] T046 [P] [US4] `frontend/src/features/participation/messages.test.ts`: `ui-api-map.md` 3절의 참여 관련 행(409 `NOT_JOINABLE`×3, `INSUFFICIENT_QUANTITY`, `PRICE_CHANGED`, `ALREADY_JOINED`, `BUSY`, 403 `ORGANIZER_NOT_ALLOWED`, 404)이 모두 서로 구분되는 문구를 낸다. 근거: FR-028, SC-007

### Implementation for User Story 4

- [ ] T047 [P] [US4] `frontend/src/mocks/handlers/participation.ts`: `POST /api/groupbuys/{id}/participations`(201 새 참여 / 200 같은 수량 재시도 — 공구가 이후 `COMPLETED`여도 200, `FAILED` 공구의 옛 참여는 409 `NOT_JOINABLE`+`reason: FAILED` / 409 `ALREADY_JOINED` / 403 `ORGANIZER_NOT_ALLOWED` / 409 `NOT_JOINABLE`+`reason` 3종 / `PRICE_CHANGED`+`currentUnitPrice` / `INSUFFICIENT_QUANTITY`+`remainingQuantity` / 마지막 수량이면 `COMPLETED` 전환), `PUT …/participations/me`(수량 변경), `DELETE …/participations/me`(204, 404 `PARTICIPATION_NOT_FOUND`). 시나리오 토글 지원. 근거: 004 계약
- [ ] T048 [US4] `frontend/src/features/participation/ParticipationPanel.tsx` + `useJoin.ts`/`useChangeQuantity.ts`/`useLeave.ts`: 수량 입력(1 이상 남은 수량 이하 정수), 내 부담 금액 미리보기, 제출 시 화면에서 확인한 `expectedUnitPrice` 전송, 처리 중 버튼 비활성화, `PRICE_CHANGED`이면 `currentUnitPrice`로 갱신하고 사용자가 다시 확인해야 제출 가능, 거부(`NOT_JOINABLE`/`INSUFFICIENT_QUANTITY`)이면 문구 + 상세 쿼리 무효화, 성공이면 상세·목록·내 활동 무효화(FR-030). 근거: FR-027~030, ui-api-map.md 4·5
- [ ] T049 [US4] `frontend/src/features/groupbuy/detail/GroupBuyDetail.tsx`에 `ParticipationPanel` 연결(`visibleActions`): 참여 전 → 참여하기, 참여 후 → 수량 변경·참여 취소(`ConfirmDialog`), 개설자·모집 종료·마감 후에는 참여 관련 버튼 없음. 모집 완료 시 `openChatUrl`이 있으면 입장 링크(새 창). 근거: FR-021, FR-022, 시나리오 25·27
- [ ] T050 [US4] `frontend/tests/e2e/join.spec.ts`: 로그인 → 상세 → 참여 → 수치 변경 확인 → 연타 시 1건, 가격 변경 시 재확인. 근거: 시나리오 21·22·26

**Checkpoint**: US1~US4 독립 동작. 여기까지가 줄이지 않는 범위다.

---

## Phase 7: User Story 5 - 내 활동 보기 (Priority: P3)

**Goal**: 내가 연 공구 / 참여한 공구 탭(S08). **독립: 이 스토리만 빼도 US1~US4에 영향 없음.**

**Independent Test**: quickstart 28.

- [ ] T051 [P] [US5] `frontend/src/features/groupbuy/mine/MyActivity.test.tsx`: 탭1 2개, 탭2 5개(내가 연 2개 포함) + 상태 뱃지, 더 보기, 취소된 공구는 어느 탭에도 없음, 항목 → 상세 이동. 근거: US5, FR-031
- [ ] T052 [P] [US5] `frontend/src/mocks/handlers/me.ts`: `GET /api/me/groupbuys?role=organizer|participant&cursor&size`(참여자 탭은 `myQuantity` 추가, 개설 시각 늦은 순, 취소 제외, 400 `role` 누락). 근거: 003 계약
- [ ] T053 [US5] `frontend/src/features/groupbuy/mine/useMyGroupBuys.ts`와 `pages/MyPage.tsx`(`/me`): 두 탭(`role`), 카드 재사용(`GroupBuyCard`), 더 보기, 빈 상태. 근거: FR-031

**Checkpoint**: US5 독립 동작.

---

## Phase 8: User Story 6 - 개설자가 입금·수령 체크하기 (Priority: P3)

**Goal**: 체크표(S09). **독립: 이 스토리만 빼도 US1~US4에 영향 없음.** 006 계약 위.

**Independent Test**: quickstart 29.

- [ ] T054 [P] [US6] `frontend/src/features/check/CheckSheet.test.tsx`: 개설자 상세(모집 완료)에서만 체크표 링크 노출, 행 필드(이름·수량·금액·입금·수령), 필터 전체/미입금/미수령 시 목록만 걸러지고 요약은 전체 기준 유지, 입금·수령 지정과 되돌리기(행 갱신 + 요약 다시 조회), 연타해도 한 번 반영(버튼 처리 중 비활성), 입금 없이 수령된 행 구분 표시, `ORGANIZER_ONLY`·`NOT_CHECKABLE` 안내 + 상세로, 참여자 상세에 본인 입금·수령 확인 상태. 근거: US6, FR-032~034
- [ ] T055 [P] [US6] `frontend/src/mocks/handlers/check.ts`: `GET …/check-sheet?filter`(개설자 제외 행, `summary`는 항상 전체), `PUT …/participations/{memberId}/payment|receipt`(`{confirmed}`, 멱등, 갱신된 행 반환, 400 개설자 본인, 403 `ORGANIZER_ONLY`, 404, 409 `NOT_CHECKABLE`). 상세 응답에 `paidConfirmedAt`·`receivedConfirmedAt` 확장. 근거: 006 계약
- [ ] T056 [US6] `frontend/src/features/check/api.ts`, `useCheckSheet.ts`, `useSetConfirmed.ts`: 쿼리 키 `['check', id, filter]`, 변이 후 체크표 무효화(요약 포함). 근거: ui-api-map.md 5
- [ ] T057 [US6] `frontend/src/pages/CheckPage.tsx`(`/groupbuys/:id/check`) + `CheckRow.tsx`: 요약(입금 완료 인원, 수령 완료 인원, 금액), 필터, 행별 입금·수령 토글, `receivedWithoutPayment` 표시, 거부 안내. 상세의 개설자 영역에 모집 완료일 때만 "참여자 체크" 링크(`visibleActions`). 체크는 알림을 보내지 않는다. 근거: FR-032~034

**Checkpoint**: US6 독립 동작.

---

## Phase 9: User Story 7 - 알림함에서 소식 확인하기 (Priority: P3)

**Goal**: 헤더 배지 + 알림함(S10). **독립: 가장 먼저 뺄 수 있는 스토리.** 007 계약 위.

**Independent Test**: quickstart 30.

- [ ] T058 [P] [US7] `frontend/src/features/notification/Notification.test.tsx`: 헤더 배지에 읽지 않은 수(화면 불러올 때 한 번 조회, 폴링 없음)(FR-035), 유형별 문구(모집 완료, 모집 실패, 정보 수정은 바뀐 항목 이름만, 공구 취소)와 시각 + 더 보기(FR-036), 눌러 읽음 처리 → 배지 감소 → 상세 이동, `linkable: false`는 이동하지 않고 내용만 + 읽음 처리 가능(FR-037). 근거: US7
- [ ] T059 [P] [US7] `frontend/src/mocks/handlers/notifications.ts`: `GET /api/notifications`(최근 순, 불투명 커서), `GET /api/notifications/unread-count`, `PUT /api/notifications/{id}/read`(멱등, 남의 알림 404). 유형 4종과 `changedFields`, `linkable` 포함. 근거: 007 계약
- [ ] T060 [US7] `frontend/src/features/notification/` 훅(`useUnreadCount`, `useNotifications`(무한), `useMarkRead`)과 `NotificationItem.tsx`: `changedFields`를 사용자 말 항목 이름(제목, 설명, 상품명, 카테고리, 구매처 링크, 개당 가격, 총 수량, 마감, 개설자 수량)으로 바꿔 표시. 근거: FR-036, 007 계약
- [ ] T061 [US7] `frontend/src/app/Header.tsx`에 벨 아이콘 + 읽지 않은 수 배지(0이면 숨김, 오류면 조용히 숨김)를 연결하고 `frontend/src/pages/NotificationsPage.tsx`(`/notifications`)를 만든다. 로그아웃·401이면 배지 쿼리도 비워진다. 근거: FR-035, FR-037

**Checkpoint**: US7 독립 동작.

---

## Phase 10: Polish & Cross-Cutting

- [ ] T062 [P] 접근성 기본: 모든 입력에 `<label>`, 키보드로 모든 버튼·링크 조작, 포커스 표시, 색 대비(텍스트 4.5:1) 점검, 터치 대상 44px. `frontend/tests/e2e/a11y.spec.ts`에 간단한 확인(레이블 누락 없음). 근거: spec 가정(기본 접근성), SC-003
- [ ] T063 [P] 가로 스크롤·레이아웃 점검: 모든 화면(S01~S10)을 360px와 1280px에서 열어 `scrollWidth <= innerWidth`, 긴 제목·긴 명단이 줄바꿈되고 깨지지 않음을 `frontend/tests/e2e/layout.spec.ts`로 확인. 근거: FR-001, SC-003, 시나리오 35
- [ ] T064 [P] 성능 점검: 라우트별 코드 분할 확인, `npm run build` 번들 크기 기록(`frontend/README.md`), 목록 첫 화면 측정은 실제 백엔드 연결 후 4단계에서(SC-006). 수치를 지어내지 않는다. 근거: plan.md Performance Goals
- [ ] T065 `frontend/vite.config.ts`·`frontend/dist/` 기준 PWA 점검: 빌드 산출물의 매니페스트·서비스 워커 확인, 오프라인 캐시가 없음을 확인(캐시 스토리지 비어 있음), 배포 후 옛 화면이 남지 않는 자동 업데이트 동작 확인. iOS Safari 홈 화면 추가는 실제 기기가 필요해 **확인 못 함**으로 기록. 근거: research.md 결정 14·미검증 가정 2·5, 시나리오 36
- [ ] T066 계약 일치 점검: `mocks/handlers/*`의 응답 모양을 `contracts/ui-api-map.md`와 `specs/001~007/contracts/`에 대조해 어긋난 곳을 표로 적는다(`frontend/README.md` 또는 승인 요약). 백엔드가 구현된 기능부터 quickstart 해당 시나리오를 `VITE_USE_MOCK` 없이 다시 돌려 결과를 기록한다. 근거: research.md 결정 8, quickstart "실제 백엔드 재확인"
- [ ] T067 `specs/008-frontend-web/quickstart.md` 36개 시나리오를 목 모드로 전부 한 번 돌려 통과/실패/확인 못 함을 승인 요약에 기록한다. 근거: quickstart.md

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2 → 스토리**: Setup과 Foundational이 끝나기 전에는 스토리를 시작하지 않는다.
- **US1(목록) → US2(로그인·상세)**: US2는 US1의 카드·라우터를 쓰지만 목록 화면 자체를 바꾸지 않는다. US1은 로그인 없이 독립이다.
- **US3·US4는 US2의 상세·세션에 의존**한다(상세의 `visibleActions` 슬롯, T035). US3·US4끼리는 독립(서로 다른 파일)이다.
- **US5·US6·US7은 US2에만 의존**하고 서로 독립이다. 어느 것이든 통째로 빼도 나머지에 영향이 없다.
- **Polish**는 포함된 스토리가 끝난 뒤.

### 병렬 기회

- Phase 1: T002~T006 병렬.
- Phase 2: T007·T008·T009·T013·T016은 서로 다른 파일이라 병렬. T010 → T011 → T012 순서(타입 → 클라이언트 → 문구).
- 각 스토리: 테스트와 목 핸들러 태스크([P])가 서로 병렬, 구현은 훅 → 컴포넌트 → 페이지 순.
- 백엔드 세션과는 완전 병렬(프론트는 계약만 본다).

## Implementation Strategy

1. **MVP**: Phase 1~3(US1) → 비로그인 목록. 휴대폰 폭에서 확인한다.
2. **P1 완료**: US2(로그인·상세)를 붙여 목록 → 상세 흐름을 닫는다.
3. **P2**: US3(개설) → US4(참여). 여기까지가 줄이지 않는 범위이고, 실제 백엔드가 001·003·004까지 구현되면 T066의 재확인을 시작한다.
4. **P3**: US5 → US6 → US7. 일정이 막히면 US7부터 뺀다.
5. 태스크마다 테스트 → 구현 → 테스트 통과 → 승인 요약 → 승인 후 커밋.

## Notes

- 서버 오류 문구, 상태 뱃지, 노출 규칙은 모두 `data-model.md`와 `ui-api-map.md`의 표가 기준이다. 표에 없는 경우를 만나면 임의로 정하지 말고 멈춰서 알린다.
- 목은 계약의 사본이다. 계약이 바뀌면(관제탑이 알려줌) 타입, 목, `ui-api-map.md`를 같이 고친다.
- 확인하지 못한 것(iOS 설치, 실기기, 실제 백엔드 성능)은 확인했다고 쓰지 않는다.
