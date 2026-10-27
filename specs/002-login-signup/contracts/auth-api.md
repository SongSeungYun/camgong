# API Contract: 가입·로그인·로그아웃

> 초안: 추천안 기준(2026-10-26 확정, [research.md](../research.md) 결정 1·3·7·9). 2026-10-28 변경 B2(안 2, 결정 13): 신청은 이메일만, 이름·비밀번호·동의는 `verify`에서 받는다. 오류 코드 이름은 오류 코드 표 정리(cross-check-2 Y3) 때 확정한다.

기본 경로 `/api/auth`. 요청·응답은 JSON. 오류 본문은 001과 같은 `ProblemDetail` + `errors[]`. 인증은 쿠키 `camgong_session`(`HttpOnly`, `SameSite=Lax`, 운영에서 `Secure`)이며 이 기능이 적용되면 `X-Member-Id` 헤더는 어떤 요청도 인증하지 않는다(FR-014).

## POST /api/auth/signup — 가입 신청

본문

| 필드 | 규칙 |
|------|------|
| `email` | 필수, 이메일 형식, 허용 학교 도메인(앞뒤 공백·대소문자 무시) |

본문은 `{ "email": "student@school.ac.kr" }` 하나다. 비밀번호·표시 이름·동의는 신청에서 받지 않는다(받으면 남이 먼저 내 이메일로 신청해 비밀번호를 정해 둘 수 있다).

**성공** `202 Accepted` — 인증 메일 발송을 요청했다. 이 시점에는 로그인할 수 없다.

```json
{ "email": "student@school.ac.kr", "resendAvailableAt": "2026-10-27T10:01:00+09:00" }
```

같은 인증 전 이메일로 다시 제출하면 토큰과 발송 한도만 갱신되고 새 메일이 발송된다(한도 안에서, 옛 토큰은 무효). 신청에 다른 값이 없으므로 유지·덮어쓰기 문제가 없다.

| 상태 | title | 조건 |
|------|-------|------|
| 400 | | 이메일 형식·필수 오류, 허용되지 않은 도메인 (`errors`에 필드) |
| 409 | `ALREADY_REGISTERED` | 이미 인증을 마친 이메일 |
| 429 | `TOO_MANY_REQUESTS` | 메일 한도(1분 간격, 24시간 5회). `Retry-After` 헤더와 `retryAt` 본문 속성 |

표시 이름은 신청에 없으므로 이름 충돌은 인증 시점에만 판정한다(FR-003).

## POST /api/auth/signup/resend — 인증 메일 재발송

본문 `{ "email": "..." }`. **성공** `202 Accepted`(위와 같은 응답). 실패는 `429 TOO_MANY_REQUESTS`(한도), `404`(신청 내역 없음 — 이미 인증을 마쳤거나 처음부터 없음은 같은 응답으로 숨긴다), `400`(형식).

## POST /api/auth/verify — 인증 링크 확인

메일의 링크는 프론트의 화면 주소(`/verify?token=…`)다. 화면이 열리면 이름·비밀번호·동의 입력 폼을 보여주고, 사용자의 제출 동작에만 이 `POST`를 보낸다(메일 스캐너가 `GET`으로 소진하지 못하게 하려는 설계).

본문 (전부 필수)

```json
{ "token": "...", "displayName": "홍길동", "password": "...", "consent": true, "consentVersion": "2026-10-28" }
```

| 필드 | 규칙 |
|------|------|
| `token` | 필수, 메일 링크의 토큰 |
| `displayName` | 필수, 2~20자, 공백만 불가 |
| `password` | 필수, 8자 이상, UTF-8 72바이트 이하 |
| `consent` | 필수, `true`여야 한다 |
| `consentVersion` | 필수, 안내문 버전 |

처리 순서: ① 토큰 조회(400 `INVALID_TOKEN` / 409 `TOKEN_USED` / 410 `TOKEN_EXPIRED`) → ② 본문 검증 오류면 `400` + `errors[]`이고 **토큰은 소진되지 않는다**(고쳐서 다시 제출) → ③ `used_at` 조건부 `UPDATE`로 선점 → ④ `member` INSERT(비밀번호는 BCrypt 해시, 동의 시각·안내문 버전도 `member`에 기록). ⑤ 이름 충돌 `409 DISPLAY_NAME_TAKEN`, 이메일 충돌 `409 ALREADY_REGISTERED`이면 같은 트랜잭션 롤백으로 토큰이 유효한 채로 남는다. 비밀번호는 응답·로그·오류 본문 어디에도 나오지 않는다.

**성공** `200 OK` + `Set-Cookie` — 가입이 완료되고 로그인된 상태다.

```json
{ "member": { "id": 12, "displayName": "홍길동" } }
```

| 상태 | title | 조건 |
|------|-------|------|
| 400 | | 본문 검증 오류(표시 이름 2~20자·공백만 불가, 비밀번호 8자 이상·72바이트 이하, 동의 `true`가 아님, 필수 누락). `errors[]`에 필드. 토큰은 소진되지 않는다 |
| 409 | `DISPLAY_NAME_TAKEN` | 인증 시점에 이름이 이미 쓰이고 있음. 토큰은 유효한 채로 남고 `displayName`을 바꿔 다시 `POST`하면 가입이 완료된다 |
| 409 | `ALREADY_REGISTERED` | 그 사이 같은 이메일이 이미 `member`에 있음(드문 경합). 롤백되어 토큰은 유효한 채로 남는다 |
| 410 | `TOKEN_EXPIRED` | 24시간이 지남 |
| 409 | `TOKEN_USED` | 이미 사용된 링크 |
| 400 | `INVALID_TOKEN` | 알 수 없는 토큰 |

## POST /api/auth/login — 로그인

본문 `{ "email": "...", "password": "..." }`

**성공** `200 OK` + `Set-Cookie`, 본문은 verify와 같은 `{ "member": { ... } }`.

| 상태 | title | 조건 |
|------|-------|------|
| 401 | `INVALID_CREDENTIALS` | 정보가 틀림 (이메일 또는 비밀번호 어느 쪽인지, 계정이 있는지 알려주지 않음) |
| (없음) | | 인증을 마치지 않은 계정은 가입 신청만 있는 상태이므로 `member`에 없다 → 존재하지 않는 계정과 **똑같이 `401 INVALID_CREDENTIALS`**로 답한다(tasks 표 b, 2026-10-27 사용자 확인 대기). `EMAIL_NOT_VERIFIED`는 쓰지 않는다. "인증이 필요하면 가입 때 받은 메일을 확인하라"는 안내는 프론트 로그인 화면의 고정 문구 |
| 429 | `LOGIN_LOCKED` | 연속 5회 실패. `Retry-After`와 `retryAt` 포함. 없는 이메일도 같은 규칙으로 잠긴다 |

## POST /api/auth/logout — 로그아웃

본문 없음. **성공** `204 No Content`, 쿠키 삭제, 서버 세션 즉시 폐기. 다른 기기의 세션은 유지된다. 로그인하지 않았어도 `204`(멱등).

## GET /api/auth/me — 현재 회원

**성공** `200 OK` `{ "member": { "id": 12, "displayName": "홍길동" } }`. 로그인하지 않았으면 `401 UNAUTHENTICATED`. 프론트가 시작할 때 로그인 여부를 아는 용도다.

## CSRF 방어 (2026-10-27 확정)

세션 쿠키는 `SameSite=Lax`이고 프론트와 API는 같은 출처로 배포한다. 상태를 바꾸는 요청(`POST`/`PUT`/`DELETE`)은 서버가 `Origin` 헤더(없으면 `Referer`)가 같은 출처인지 확인하고, 다르면 `403`으로 거부한다. 요청 본문은 `application/json`만 받는다. 별도 CSRF 토큰 헤더는 쓰지 않으므로 프론트는 추가로 보낼 것이 없다. 다른 출처(프론트와 API를 도메인으로 분리)로 배포하게 되면 이 결정을 다시 연다.

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
