# API Contract: 회원 탈퇴

## POST /api/auth/withdraw — 탈퇴

**인증**: 필요(세션). Origin 검사 대상(002 CSRF 규칙).

본문 `{ "password": "..." }`

**성공** `204 No Content`
- 응답에 세션 쿠키 삭제(`Max-Age=0`)를 넣는다(로그아웃과 같은 방식).
- 그 회원의 다른 세션도 모두 끝난다.

| 상태 | title | 조건 |
|------|-------|------|
| 400 | `Bad Request` | `password` 누락·빈 값 (`errors`에 `password`) |
| 400 | `INVALID_PASSWORD` | 비밀번호가 틀림 (`errors: [{ "field": "password", "message": "비밀번호가 맞지 않습니다." }]`). 002 로그인 실패 횟수에 들어간다 |
| 401 | `UNAUTHENTICATED` | 세션 없음·만료(이미 탈퇴한 경우 포함) |
| 409 | `ACTIVE_GROUPBUY` | 모집 중(마감 확정 대기 포함) 공구에 개설자나 참여자로 있음. 아래 `blockers` 포함. 아무것도 바뀌지 않는다 |
| 429 | `LOGIN_LOCKED` | 002 잠금 상태. `Retry-After`, `retryAt` 포함 |

409 본문(ProblemDetail 확장):

```json
{
  "title": "ACTIVE_GROUPBUY",
  "status": 409,
  "detail": "진행 중인 공구가 있어 탈퇴할 수 없습니다.",
  "blockers": [
    { "groupBuyId": 3, "title": "햇반 24개 공구", "role": "ORGANIZER", "status": "OPEN" },
    { "groupBuyId": 7, "title": "휴지 공구", "role": "PARTICIPANT", "status": "PENDING_CLOSE" }
  ]
}
```

- `role`: `ORGANIZER` | `PARTICIPANT`.
- `status`: 003 표시 상태로 `OPEN` | `PENDING_CLOSE`다.
- 정렬: `groupBuyId` 오름차순.

## 다른 계약에 미치는 영향

- **003 상세·체크표·참여자 명단**: 탈퇴한 회원의 `name`/`organizerName`/`displayName`은 `"탈퇴한 회원"`이다. 필드 모양은 그대로다.
- **001 개설, 004 새 참여**: 요청한 회원이 그 순간 탈퇴 처리됐으면 `401 UNAUTHENTICATED`. 보통은 세션이 먼저 끝나 같은 401이 난다.
- **002 로그인**: 탈퇴 계정은 없는 계정과 똑같이 `401 INVALID_CREDENTIALS`.
- **002 가입**: 탈퇴한 회원의 이메일·표시 이름을 다시 쓸 수 있다. 표시 이름 "탈퇴한 회원"은 예약어라 거부한다(400 `errors[displayName]`, "쓸 수 없는 이름입니다."). 판정은 이름을 NFC로 모은 뒤 **모든 공백 문자(유니코드 공백 포함, 가운데 공백도), 보이지 않는 글자(002 `Keys.isInvisible`), 결합 표시(Unicode Mn: 변형 선택자 U+FE00–FE0F·U+E0100–E01EF, U+034F 등)를 지운 값**이 `탈퇴한회원`과 같은지로 한다. `탈퇴한회원`, `탈퇴한  회원`, `탈퇴한　회원`(전각 공백)도 거부한다.
- **002 모든 보호 경로**: 탈퇴한 회원의 세션은 없는 세션과 같다(401).
- **007 알림**: 탈퇴한 회원에게는 새 알림을 쓰지 않는다.
