# API Contract: 가입·로그인·로그아웃

> 초안: 추천안 기준(2026-10-26 확정, [research.md](../research.md) 결정 1·3·7·9). 오류 코드 이름은 오류 코드 표 정리(cross-check-2 Y3) 때 확정한다.

기본 경로 `/api/auth`. 요청·응답은 JSON. 오류 본문은 001과 같은 `ProblemDetail` + `errors[]`. 인증은 쿠키 `camgong_session`(`HttpOnly`, `SameSite=Lax`, 운영에서 `Secure`)이며 이 기능이 적용되면 `X-Member-Id` 헤더는 어떤 요청도 인증하지 않는다(FR-014).

## POST /api/auth/signup — 가입 신청

본문

| 필드 | 규칙 |
|------|------|
| `email` | 필수, 이메일 형식, 허용 학교 도메인(앞뒤 공백·대소문자 무시) |
| `displayName` | 필수, 2~20자, 공백만 불가 |
| `password` | 필수, 8자 이상 (최대 길이는 spec 보완 제안 1) |
| `consent` | 필수, `true`여야 한다 |
| `consentVersion` | 필수, 안내문 버전 |

**성공** `202 Accepted` — 인증 메일 발송을 요청했다. 이 시점에는 로그인할 수 없다.

```json
{ "email": "student@school.ac.kr", "resendAvailableAt": "2026-10-27T10:01:00+09:00" }
```

같은 인증 전 이메일로 다시 제출하면 새 정보로 바뀌고 새 메일이 발송된다(한도 안에서).

| 상태 | title | 조건 |
|------|-------|------|
| 400 | | 형식·길이·필수 오류, 동의 안 함, 허용되지 않은 도메인 (`errors`에 필드) |
| 409 | `ALREADY_REGISTERED` | 이미 인증을 마친 이메일 |
| 429 | `TOO_MANY_REQUESTS` | 메일 한도(1분 간격, 24시간 5회). `Retry-After` 헤더와 `retryAt` 본문 속성 |

표시 이름이 이미 쓰이고 있어도 신청 단계에서는 거부하지 않는다(인증 시점에 판정, FR-003).

## POST /api/auth/signup/resend — 인증 메일 재발송

본문 `{ "email": "..." }`. **성공** `202 Accepted`(위와 같은 응답). 실패는 `429 TOO_MANY_REQUESTS`(한도), `404`(신청 내역 없음 — 이미 인증을 마쳤거나 처음부터 없음은 같은 응답으로 숨긴다), `400`(형식).

## POST /api/auth/verify — 인증 링크 확인

메일의 링크는 프론트의 화면 주소(`/verify?token=…`)다. 화면이 열리면 사용자의 확인 동작에 이 `POST`를 보낸다(메일 스캐너가 `GET`으로 소진하지 못하게 하려는 설계).

본문 `{ "token": "...", "displayName": "(선택, 이름 충돌 시 새 이름)" }`

**성공** `200 OK` + `Set-Cookie` — 가입이 완료되고 로그인된 상태다.

```json
{ "member": { "id": 12, "displayName": "홍길동" } }
```

| 상태 | title | 조건 |
|------|-------|------|
| 409 | `DISPLAY_NAME_TAKEN` | 인증 시점에 이름이 이미 쓰이고 있음. 토큰은 유효한 채로 남고 `displayName`을 바꿔 다시 `POST`하면 가입이 완료된다 |
| 410 | `TOKEN_EXPIRED` | 24시간이 지남 |
| 409 | `TOKEN_USED` | 이미 사용된 링크 |
| 400 | `INVALID_TOKEN` | 알 수 없는 토큰 |

## POST /api/auth/login — 로그인

본문 `{ "email": "...", "password": "..." }`

**성공** `200 OK` + `Set-Cookie`, 본문은 verify와 같은 `{ "member": { ... } }`.

| 상태 | title | 조건 |
|------|-------|------|
| 401 | `INVALID_CREDENTIALS` | 정보가 틀림 (이메일 또는 비밀번호 어느 쪽인지, 계정이 있는지 알려주지 않음) |
| 403 | `EMAIL_NOT_VERIFIED` | 인증을 마치지 않은 계정은 가입 신청만 있는 상태이므로 `member`에 없다 → 이 경우도 `401`과 같이 취급하되 안내만 "이메일 인증이 필요하다"를 덧붙일지는 결정 대기(FR-010) |
| 429 | `LOGIN_LOCKED` | 연속 5회 실패. `Retry-After`와 `retryAt` 포함. 없는 이메일도 같은 규칙으로 잠긴다 |

## POST /api/auth/logout — 로그아웃

본문 없음. **성공** `204 No Content`, 쿠키 삭제, 서버 세션 즉시 폐기. 다른 기기의 세션은 유지된다. 로그인하지 않았어도 `204`(멱등).

## GET /api/auth/me — 현재 회원

**성공** `200 OK` `{ "member": { "id": 12, "displayName": "홍길동" } }`. 로그인하지 않았으면 `401 UNAUTHENTICATED`. 프론트가 시작할 때 로그인 여부를 아는 용도다.

## 보호된 모든 엔드포인트

로그인이 필요한 엔드포인트(공구 상세, 개설, 수정, 취소, 참여, 수량 변경, 참여 취소, 내 활동, 알림, 체크표)는 세션이 없거나 만료되면 `401 UNAUTHENTICATED`를 돌려주고, 프론트가 로그인 화면으로 보낸다. 공구 목록(`GET /api/groupbuys`)은 공개다.

다른 회원에게 보이는 회원 정보는 `displayName`뿐이다. 이메일과 비밀번호는 어떤 응답에도 없다.

## 설정

| 키 | 기본값 | 뜻 |
|----|--------|-----|
| `camgong.auth.allowed-email-domains` | (사용자 제공) | 허용 학교 이메일 도메인 목록 |
| `camgong.auth.session-ttl` | `14d` | 슬라이딩 유지 기간 |
| `camgong.auth.session-touch-interval` | `10m` | `last_used_at` 갱신 최소 간격 |
| `camgong.auth.verify-token-ttl` | `24h` | 인증 링크 유효 시간 |
| `camgong.auth.mail.resend-interval` / `daily-limit` | `1m` / `5` | 메일 한도 |
| `camgong.auth.login.max-failures` / `lock-duration` | `5` / `15m` | 로그인 잠금 |
| `camgong.mail.*` | (환경변수) | SMTP 접속 정보. 저장소에 넣지 않는다 |
