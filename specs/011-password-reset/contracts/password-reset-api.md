# API Contract: 비밀번호 재설정

로그인 없이 부른다. 두 경로 모두 Origin 검사 대상(002 CSRF 규칙). 오류 본문은 002와 같은 `ProblemDetail` + `errors[]`.

## POST /api/auth/password-reset — 재설정 요청

본문 `{ "email": "student@snu.ac.kr" }`

**성공** `202 Accepted`

```json
{ "retryAt": "2026-11-22T10:01:00Z" }
```

- 가입한 회원이든, 인증 전이든, 탈퇴했든, 처음 보는 주소든 **같은 응답**이다. 메일은 가입을 마치고 탈퇴하지 않은 회원에게만 보낸다(응답 뒤에, 비동기).
- `retryAt`: 이 주소로 다시 요청할 수 있는 가장 이른 시각(직전 요청 + 1분).

| 상태 | title | 조건 |
|------|-------|------|
| 400 | `Bad Request` | 이메일 누락·형식 오류·254자 초과, 대학 메일이 아님(`errors[email]` "대학 메일로만 가입할 수 있습니다.") |
| 429 | `TOO_MANY_REQUESTS` | 같은 주소의 직전 요청 뒤 1분 안, 또는 24시간 5회. `Retry-After`, `retryAt`. 가입 여부와 무관하게 센다 |

## POST /api/auth/password-reset/confirm — 새 비밀번호 정하기

본문 `{ "token": "…", "password": "…" }`

**성공** `204 No Content`. 그 회원의 모든 세션이 끝나고 로그인 실패 잠금이 풀린다. 쿠키는 건드리지 않는다(자동 로그인 없음).

| 상태 | title | 조건 |
|------|-------|------|
| 400 | `INVALID_TOKEN` | 토큰 누락, 없는 토큰, 새 요청으로 무효가 된 토큰, 그 사이 탈퇴한 회원 |
| 400 | `Bad Request` | 비밀번호 규칙 위반(002 가입과 같다: 8자 이상, 72바이트 이하, 공백만 불가). `errors[password]`. 토큰은 소진되지 않는다 |
| 409 | `TOKEN_USED` | 이미 쓴 링크 |
| 410 | `TOKEN_EXPIRED` | 요청 뒤 1시간이 지남 |

## 다른 계약에 미치는 영향

- **010 탈퇴**: 탈퇴하면 그 주소의 재설정 요청 기록을 지운다.
- **002 로그인**: 재설정 요청은 로그인 실패 횟수에 세지 않는다. 재설정 성공은 실패 기록을 지운다.
- **008 ui-api-map**: S02 로그인에 "비밀번호를 잊었어요", 새 화면 `/password-reset`, `/password-reset/confirm?token=`.
