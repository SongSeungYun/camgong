# API Contract: 개설자용 입금·수령 체크

> 초안: 추천안 기준(2026-10-26 확정, [research.md](../research.md) 결정 1·5·8). 오류 코드 이름은 오류 코드 표 정리(cross-check-2 Y3) 때 확정한다.

기본 경로 `/api/groupbuys/{id}`. 요청·응답은 JSON, 시간은 오프셋 포함 ISO-8601. 모든 요청에 `X-Member-Id` 헤더(임시 인증, 002에서 교체). 오류 본문은 001과 같은 `ProblemDetail` + `errors[]`.

## GET /api/groupbuys/{id}/check-sheet — 체크표

**인증**: 필수, 개설자만. 모집 완료(`COMPLETED`)인 공구에서만.

| 쿼리 | 값 | 기본 | 설명 |
|------|-----|------|------|
| `filter` | `all`, `unpaid`, `unreceived` | `all` | 목록만 거른다. `summary`는 항상 전체 기준 |

**성공** `200 OK`

```json
{
  "summary": {
    "participantCount": 2,
    "paidCount": 1,
    "receivedCount": 0,
    "amountDue": 18000,
    "amountConfirmed": 10800
  },
  "rows": [
    {
      "memberId": 2,
      "name": "홍길동",
      "quantity": 9,
      "amount": 10800,
      "paidConfirmedAt": "2026-10-27T10:00:00+09:00",
      "receivedConfirmedAt": null,
      "receivedWithoutPayment": false
    }
  ]
}
```

- `rows`는 개설자를 제외한 참여자다. `amount = 개당 가격 × quantity`, `amountDue`는 개설자를 뺀 모든 참여자의 금액 합, `amountConfirmed`는 입금이 확인된 참여자의 금액 합이다.
- `receivedWithoutPayment`: 입금이 확인되지 않았는데 수령이 확인된 행(화면에서 구분해 표시, FR-002).
- 참여자가 없으면 `rows: []`, `participantCount: 0`이다(오류가 아니다).

| 상태 | title | 조건 |
|------|-------|------|
| 400 | | 지원하지 않는 `filter` |
| 401 | | 회원 식별 실패 |
| 403 | `ORGANIZER_ONLY` | 요청자가 개설자가 아님 |
| 404 | | 공구가 없거나 취소됨 |
| 409 | `NOT_CHECKABLE` | 모집 완료가 아님(모집 중, 마감됨, 모집 실패) |

## PUT /api/groupbuys/{id}/participations/{memberId}/payment — 입금 확인 지정·되돌리기
## PUT /api/groupbuys/{id}/participations/{memberId}/receipt — 수령 확인 지정·되돌리기

본문 `{ "confirmed": true | false }`. `true`는 확인됨으로 지정(이미 확인됐으면 시각을 바꾸지 않음), `false`는 확인 전으로 되돌림(시각 삭제). 입금과 수령은 서로 영향이 없다.

**성공** `200 OK`, 응답은 갱신된 행(체크표의 `rows` 항목과 같은 모양).

| 상태 | title | 조건 |
|------|-------|------|
| 400 | | 본문이 올바르지 않음(`confirmed` 누락·형식 오류), 또는 `memberId`가 개설자 본인 (`errors`에 `memberId`) |
| 401 | | 회원 식별 실패 |
| 403 | `ORGANIZER_ONLY` | 요청자가 개설자가 아님 |
| 404 | | 공구가 없거나 취소됨, 또는 `memberId`가 이 공구의 참여자가 아님 |
| 409 | `NOT_CHECKABLE` | 모집 완료가 아님 |

체크는 알림을 발생시키지 않는다(FR-008). 응답과 이벤트 어디에도 다른 참여자의 정보를 담지 않는다.

## 003 상세 응답 확장 (참여자 본인 상태, 결정 6)

`GET /api/groupbuys/{id}`의 `myParticipation`에 필드를 더한다. 개설자의 `myParticipation`에는 이 필드가 없다.

```json
"myParticipation": {
  "quantity": 6,
  "amount": 7200,
  "paidConfirmedAt": "2026-10-27T10:00:00+09:00",
  "receivedConfirmedAt": null
}
```

다른 참여자의 상태는 어떤 응답에도 내려가지 않는다(체크표는 개설자에게만).
