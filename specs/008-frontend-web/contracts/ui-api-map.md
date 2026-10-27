# UI ↔ API 매핑

> 추천안 기준(2026-10-27 확정, [research.md](../research.md)). 서버 계약이 기준이고, 이 표는 화면이 어떤 엔드포인트를 어떻게 쓰고 오류를 어떤 말로 바꾸는지의 기준표다. MSW 핸들러와 화면 테스트는 이 표와 대조한다. 계약이 바뀌면 이 문서를 같이 고친다.

## 1. 화면 → 엔드포인트

| 화면 | 주소(앱 라우트) | 호출 | 인증 | 계약 |
|------|------------------|------|------|------|
| S03 목록 | `/` | `GET /api/groupbuys?status&category&q&sort&cursor&size` | 불필요 | 003 |
| S01 가입 | `/signup` | `POST /api/auth/signup`, `POST /api/auth/signup/resend` | 불필요 | 002 |
| 인증 확인 | `/verify?token=…` | `POST /api/auth/verify` (사용자의 확인 동작으로 전송) | 불필요 | 002 |
| S02 로그인 | `/login` | `POST /api/auth/login`, `POST /api/auth/logout`, 시작 시 `GET /api/auth/me` | — | 002 |
| S04 상세 | `/groupbuys/:id` | `GET /api/groupbuys/{id}` | 필수 | 003, 006 확장 |
| S05 개설 | `/groupbuys/new` | `POST /api/groupbuys` | 필수 | 001 |
| S06 수정 | `/groupbuys/:id/edit` | `GET /api/groupbuys/{id}`, `PUT /api/groupbuys/{id}`, `POST /api/groupbuys/{id}/cancel` | 필수(개설자) | 001 |
| S07 참여 | 상세 안 시트/폼 | `POST /api/groupbuys/{id}/participations`, `PUT …/participations/me`, `DELETE …/participations/me` | 필수 | 004 |
| S08 내 활동 | `/me` | `GET /api/me/groupbuys?role=organizer|participant&cursor&size` | 필수 | 003 |
| S09 체크표 | `/groupbuys/:id/check` | `GET …/check-sheet?filter`, `PUT …/participations/{memberId}/payment`, `PUT …/receipt` | 필수(개설자) | 006 |
| S10 알림함 | `/notifications` | `GET /api/notifications`, `PUT /api/notifications/{id}/read`; 헤더 배지 `GET /api/notifications/unread-count` | 필수 | 007 |

비로그인으로 보호 라우트를 열면 로그인(`/login`)으로 보내고 로그인 뒤에는 항상 `/`로 간다(FR-002). 존재하지 않는 앱 라우트는 찾을 수 없음 화면으로 간다.

## 2. 요청 공통

- 모든 요청에 쿠키를 포함한다(`credentials: 'include'`). `X-Member-Id`는 보내지 않는다.
- CSRF: 별도 토큰 헤더 없음(002 계약: `SameSite=Lax` + 서버 `Origin` 검증, 같은 출처 배포). 본문은 `application/json`으로만 보낸다. `Origin` 때문에 거부(403)되면 일반 오류로 취급한다.
- 시각은 오프셋 포함 ISO-8601로 보낸다(`2026-11-01T18:00:00+09:00`).

## 3. 오류 → 안내 문구 (FR-005, FR-006, FR-028)

문구는 초안이며 구현 때 다듬는다. 서버의 `detail`은 노출하지 않는다.

| 상태 | 코드(`title`) / `reason` | 화면 동작·문구 |
|------|---------------------------|----------------|
| 400 | (필드 오류 `errors[]`) | 해당 폼 필드 옆에 모두 표시. 매핑 안 되는 것은 폼 상단. 입력값 유지 |
| 401 | `UNAUTHENTICATED` / (없음) | 세션 비우고 로그인으로. "로그인이 필요해요. 입력하던 내용은 저장되지 않아요." (폼에서 발생한 경우) |
| 401 | `INVALID_CREDENTIALS` | "이메일 또는 비밀번호가 맞지 않아요." (어느 쪽인지 알리지 않음). 인증 전 계정의 로그인도 같은 응답이다(002 확정 2026-10-27, `EMAIL_NOT_VERIFIED` 없음). 로그인 화면은 이 문구 아래에 **고정 안내** "가입했는데 아직 인증 전이라면 인증 메일 다시 받기"와 재발송 진입을 항상 둔다 |
| 403 | `ORGANIZER_NOT_ALLOWED` | "개설자는 직접 참여할 수 없어요. 수량은 공구 수정에서 바꿔요." |
| 403 | `ORGANIZER_ONLY` | "개설자만 쓸 수 있어요." 상세로 |
| 404 | (공구·참여 없음/취소) | "찾을 수 없는 공구예요." + 목록으로 (FR-023) |
| 404 | `PARTICIPATION_NOT_FOUND` | "참여 내역이 없어요." 상세 새로고침 |
| 409 | `NOT_JOINABLE` / `DEADLINE_PASSED` | "마감 시간이 지나서 참여할 수 없어요." + 최신 상세 다시 불러옴 |
| 409 | `NOT_JOINABLE` / `COMPLETED` | "이미 모집이 끝난 공구예요." + 최신 상세 |
| 409 | `NOT_JOINABLE` / `FAILED` | "모집에 실패한 공구예요." + 최신 상세 |
| 409 | `NOT_MODIFIABLE` / `COMPLETED`·`DEADLINE_PASSED` | "이미 모집이 끝났거나 마감이 지나서 수정·취소할 수 없어요." + 최신 상세 |
| 409 | `INSUFFICIENT_QUANTITY` (+`remainingQuantity`) | "남은 수량이 부족해요. 지금 남은 수량은 N개예요." + 최신 상세 |
| 409 | `PRICE_CHANGED` (+`currentUnitPrice`) | "개당 가격이 바뀌었어요(N원). 금액을 다시 확인해 주세요." 새 가격으로 갱신, 재확인해야 제출 가능 |
| 409 | `ALREADY_JOINED` | "이미 참여했어요. 수량은 '수량 변경'을 써요." |
| 409 | `BUSY` | "요청이 몰렸어요. 잠시 뒤 다시 시도해 주세요." |
| 409 | `DUPLICATE_REQUEST` | 같은 공구가 이미 열렸다고 안내하고 해당 상세로 (001 계약) |
| 409 | `NOT_CHECKABLE` | "모집이 완료된 공구에서만 체크할 수 있어요." |
| 409 | `ALREADY_REGISTERED` | "이미 가입된 이메일이에요." + 로그인 안내 |
| 409 | `DISPLAY_NAME_TAKEN` | "이미 쓰이는 이름이에요. 다른 이름을 입력해 주세요." (인증 확인 화면에서 이름 입력란 노출) |
| 409 / 410 | `TOKEN_USED` / `TOKEN_EXPIRED` | "이미 쓴 링크예요." / "링크가 만료됐어요." + 재발송 |
| 400 | `INVALID_TOKEN` | "올바르지 않은 링크예요." + 재발송 |
| 429 | `TOO_MANY_REQUESTS` (+`retryAt`, `Retry-After`) | "잠시 뒤에 다시 시도해 주세요." (남은 시간 표시) |
| 429 | `LOGIN_LOCKED` (+`retryAt`) | "로그인 시도가 너무 많아요. 잠시 뒤 다시 시도해 주세요." |
| 5xx / 네트워크 | — | "잠시 문제가 생겼어요. 다시 시도해 주세요." + 다시 시도 버튼. 입력값 유지. 서버 정보 노출 안 함 |
| 그 외 | 알 수 없는 코드 | 상태 코드 기준 일반 문구 (화면이 멈추지 않음) |

## 4. 멱등과 중복 제출 (FR-007, FR-029)

- 제출 버튼은 처리 중 비활성화한다.
- 참여: 재시도에 `200`이 와도 `201`과 같은 성공으로 보인다(공구가 그 사이 `COMPLETED`여도 같은 수량이면 성공, 004 결정). `FAILED` 공구의 옛 참여 재시도는 위 표의 `NOT_JOINABLE`/`FAILED`로 보인다.
- 체크 PUT은 목표 상태를 보내는 멱등 호출이라 연타해도 한 번 반영과 같다. 응답 행으로 해당 행만 갱신하고 요약은 다시 조회한다.

## 5. 쿼리 무효화 (FR-030)

| 변경 성공 | 다시 불러올 것 |
|-----------|----------------|
| 개설 | 목록, 내 활동, (이동할) 상세 |
| 수정·취소 | 상세(취소는 목록·내 활동 포함) |
| 참여·수량 변경·참여 취소 | 상세, 목록, 내 활동 |
| 입금·수령 체크 | 체크표(요약 포함) |
| 알림 읽음 | 알림함, 읽지 않은 수 |
| 로그아웃·401 | 전부 비움 |
