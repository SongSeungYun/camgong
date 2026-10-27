# API Contract: 공구 참여·수량 변경·참여 취소

기본 경로 `/api/groupbuys/{id}/participations`. 요청·응답은 JSON. 모든 요청에 `X-Member-Id` 헤더가 필요하다(임시 인증, 002에서 교체).
헤더가 없거나 없는 회원이면 `401`. 오류 본문은 001과 같은 `ProblemDetail` + `errors[]`.

## 요청 본문 (참여·수량 변경)

| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| quantity | integer | O | ≥ 1 (상한은 처리 시점의 남은 수량으로 판정) |
| expectedUnitPrice | integer | O | 사용자가 화면에서 확인한 개당 가격. 현재 값과 다르면 거부 |

## POST /api/groupbuys/{id}/participations — 참여

**성공**: `201 Created`(새 참여), 본문은 아래 응답. 이미 참여했고 요청 수량이 현재 수량과 같으면 **공구 상태와 무관하게**(이후 `COMPLETED`가 되었더라도) 변화 없이 `200 OK`로 현재 상태를 돌려준다(재시도 안전, FR-004). 단 공구가 `FAILED`이면 옛 참여라도 409 `NOT_JOINABLE`(`reason: FAILED`), 취소된 공구는 404다. 모집 중이 아닌 공구에 **처음** 하는 참여는 거부한다.

| 상태 | title | 조건 |
|------|-------|------|
| 400 | | 수량이 1 미만이거나 정수가 아님, 필드 누락 |
| 401 | | 회원 식별 실패 |
| 403 | `ORGANIZER_NOT_ALLOWED` | 요청자가 개설자. 안내: "개설자는 직접 참여할 수 없어요. 수량은 공구 수정에서 바꿔요." |
| 404 | | 공구가 없거나 취소됨 |
| 409 | `ALREADY_JOINED` | 이미 참여했고 수량이 다름(수량 변경을 쓸 것) |
| 409 | `NOT_JOINABLE` | 모집 중이 아님(완료, 실패)이거나 마감이 지남. 본문 `reason`: `DEADLINE_PASSED` / `COMPLETED` / `FAILED` (`docs/architecture.md` 6.1) |
| 409 | `PRICE_CHANGED` | `expectedUnitPrice`가 현재와 다름. 본문에 `currentUnitPrice` |
| 409 | `INSUFFICIENT_QUANTITY` | 수량이 남은 수량보다 큼. 본문에 `remainingQuantity`(공구 전체의 남은 수량. 수량 변경(PUT) 때도 내 수량을 뺀 값이며, 변경 가능한 최대는 `remainingQuantity` + 내 현재 수량) |
| 409 | `BUSY` | 낙관적 방식에서 재시도 소진(다른 방식에서는 나오지 않음) |

## PUT /api/groupbuys/{id}/participations/me — 수량 변경

본문은 위 요청 본문. 새 수량은 `1 ≤ 새 수량 ≤ 남은 수량 + 기존 내 수량`. 같은 수량이면 변화 없이 `200`.

**성공**: `200 OK`, 응답 본문은 아래와 같다. 변경 결과 확보 수량이 총 수량과 같아지면 `status = COMPLETED`.

| 상태 | title | 조건 |
|------|-------|------|
| 400, 401 | | 위와 같다 |
| 403 | `ORGANIZER_NOT_ALLOWED` | 요청자가 개설자(공구 수정을 쓸 것) |
| 404 | `PARTICIPATION_NOT_FOUND` | 이 공구에 내 참여가 없음 |
| 404 | | 공구가 없거나 취소됨 |
| 409 | `NOT_JOINABLE`, `PRICE_CHANGED`, `INSUFFICIENT_QUANTITY`, `BUSY` | 위와 같다 |

## DELETE /api/groupbuys/{id}/participations/me — 참여 취소

본문 없음.

**성공**: `204 No Content`. 참여 기록이 지워지고 확보 수량이 그만큼 줄며 공구는 유지된다.

| 상태 | title | 조건 |
|------|-------|------|
| 401 | | 회원 식별 실패 |
| 403 | `ORGANIZER_NOT_ALLOWED` | 요청자가 개설자(공구 취소를 쓸 것) |
| 404 | `PARTICIPATION_NOT_FOUND` | 내 참여가 없음 |
| 404 | | 공구가 없거나 취소됨 |
| 409 | `NOT_JOINABLE` | 모집 완료·실패이거나 마감이 지남. 본문 `reason` 포함(위와 같음) |
| 409 | `BUSY` | 낙관적 방식에서 재시도 소진 |

## 응답 본문 (참여·수량 변경)

```json
{
  "groupBuyId": 1,
  "quantity": 6,
  "amount": 7200,
  "securedQuantity": 15,
  "remainingQuantity": 9,
  "status": "OPEN"
}
```

`amount = 개당 가격 × quantity`. 본인 정보만 담는다(FR-014). 다른 참여자의 이름·수량은 포함하지 않는다.

## 이벤트 (내부)

| 이벤트 | 필드 |
|--------|------|
| `GroupBuyCompleted` | `groupBuyId`, `recipientIds`(개설자 포함 모든 참여자) |

커밋 후에만 전달된다. 참여·수량 변경·참여 취소 자체는 이벤트가 없다.

## 구현 방식 설정

`camgong.quantity-gate` = `pessimistic`(기본) | `conditional` | `optimistic` | `none`. `none`은 `benchmark` 프로파일에서만 허용한다. API 동작은 방식과 무관하게 같고, 차이는 동시 요청 처리 방식과 성능뿐이다(`none`은 정확성이 깨질 수 있다).
