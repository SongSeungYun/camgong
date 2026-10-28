# API Contract: 공구 개설

기본 경로 `/api/groupbuys`. 요청·응답은 JSON. 모든 요청에 `X-Member-Id: <회원 id>` 헤더가 필요하다(4단계에서 인증으로 교체).
헤더가 없거나 존재하지 않는 회원이면 `401`.

시간 필드는 오프셋 포함 ISO-8601 (`2026-11-01T18:00:00+09:00`).

## 공통 요청 본문 (개설·수정 공용)

| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| title | string | O | 공백만 불가, 1~100자 |
| description | string | X | 최대 2000자 |
| itemName | string | O | 공백만 불가, 1~100자 |
| unitPrice | integer | O | 1~1,000,000 (원) |
| totalQuantity | integer | O | 2~1000 |
| deadline | datetime | O | 개설: 현재 이후 & 개설 시점 +30일 이내. 수정: 현재 값 이상 & 개설 시점 +30일 이내 |
| organizerQuantity | integer | O | ≥ 1, 개설: < totalQuantity. 수정: 다른 참여자 수량과 합쳐 totalQuantity 이하 |
| category | string | O | `FOOD`, `DAILY`, `KITCHEN`, `HEALTH_BEAUTY`, `STATIONERY`, `ETC` 중 하나 |
| itemUrl | string | O | https URL, 최대 500자 (구매처 링크) |
| openChatUrl | string | O | https URL, 최대 500자 |

## POST /api/groupbuys — 개설 (FR-001~010, 016)

**성공** `201 Created`, `Location: /api/groupbuys/{id}`, 본문은 아래 응답.

개설 직후 응답 예: `securedQuantity = organizerQuantity`, `remainingQuantity = totalQuantity − organizerQuantity`, `status = OPEN`.

| 상태 | 조건 |
|------|------|
| 400 | 입력 오류 (`errors[]`에 필드별 사유) |
| 401 | 회원 식별 실패 |
| 409 `DUPLICATE_REQUEST` | 같은 회원이 모든 입력값이 같은 요청을 10초 안에 다시 보냄 |

## PUT /api/groupbuys/{id} — 수정 (FR-011~013, 015~017)

본문은 공통 요청 본문 전체(생략한 선택 필드는 지워진 것으로 본다). 개설자만 가능.

**성공** `200 OK`, 응답 본문은 아래와 같다. 수정 결과 확보 수량이 총 수량과 같아지면 `status = COMPLETED`.

| 상태 | 조건 |
|------|------|
| 400 | 입력 오류. 마감 단축/상한 초과, 총 수량 < 확보 수량, 개설자 수량 변경으로 합이 총 수량 초과 포함 |
| 401 | 회원 식별 실패 |
| 403 | 개설자가 아님 |
| 404 | 공구가 없거나 취소됨 |
| 409 `NOT_MODIFIABLE` | `OPEN`이 아니거나(COMPLETED, FAILED) 마감이 지남. 본문 `reason`: `COMPLETED` / `FAILED` / `DEADLINE_PASSED` (`FAILED`는 005에서 모집 실패로 확정된 공구, 004 참여 거부와 같은 값. 2026-10-28 결정) |

부수 효과: 오픈채팅 링크 외 필드(구매처 링크 포함)가 바뀌었고 개설자 외 참여자가 있으면 `GroupBuyChanged` 이벤트 발행(알림 기능이 소비). 수정으로 확보 수량이 총 수량과 같아져 모집 완료가 되면 개설자 포함 전원 대상 `GroupBuyCompleted`도 발행한다.

## POST /api/groupbuys/{id}/cancel — 취소 (FR-011, 014, 015)

본문 없음. 개설자만 가능.

**성공** `204 No Content`. 공구는 `CANCELLED`가 되어 이후 조회(GET)에서 404, 참여 불가.

| 상태 | 조건 |
|------|------|
| 401 | 회원 식별 실패 |
| 403 | 개설자가 아님 |
| 404 | 공구가 없거나 취소됨 |
| 409 `NOT_MODIFIABLE` | `OPEN`이 아니거나 마감이 지남. 본문 `reason` 포함(위와 같음) |

부수 효과: 개설자 외 참여자가 있으면 `GroupBuyCancelled` 이벤트 발행.

## GET /api/groupbuys/{id} — 상세 (개설 결과 확인용 최소 조회)

목록·검색은 이 기능 범위 밖이다. 개설·수정 결과와 FR-017 공개 규칙을 검증하기 위한 단건 조회만 둔다. 003이 이 응답을 확장해 대체하므로 구현은 003과 함께 정리한다.

**성공** `200 OK`

| 상태 | 조건 |
|------|------|
| 401 | 회원 식별 실패 |
| 404 | 없거나 `CANCELLED` |

`openChatUrl` 노출 규칙(FR-017):

| 요청자 | 공구 상태 | openChatUrl |
|--------|-----------|-------------|
| 개설자 | OPEN, COMPLETED | 값 있음 |
| 참여자 | COMPLETED | 값 있음 |
| 참여자 | OPEN | null |
| 참여자가 아닌 회원 | 모두 | null |

## 응답 본문

```json
{
  "id": 1,
  "title": "햇반 24개 공구",
  "description": null,
  "itemName": "햇반 210g",
  "unitPrice": 1200,
  "totalQuantity": 24,
  "securedQuantity": 9,
  "remainingQuantity": 15,
  "deadline": "2026-11-01T18:00:00+09:00",
  "status": "OPEN",
  "organizerId": 1,
  "organizerQuantity": 9,
  "category": "FOOD",
  "itemUrl": "https://example.com/item/123",
  "openChatUrl": null,
  "createdAt": "2026-10-25T07:30:00+09:00"
}
```

## 오류 본문

RFC 7807 `ProblemDetail`에 `errors` 속성을 더한다.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "입력값이 올바르지 않습니다.",
  "errors": [
    { "field": "deadline", "message": "마감 시각은 현재보다 미래여야 합니다." },
    { "field": "organizerQuantity", "message": "개설자 수량은 총 수량보다 작아야 합니다." }
  ]
}
```

검증은 두 단계다(2026-10-28 실서버 통합에서 확인). 필드 형식 검증(필수·형식·범위)이 먼저이고, 필드 사이 관계 검증(`organizerQuantity < totalQuantity` 등)은 형식 검증을 통과한 뒤에 수행된다. 그래서 두 종류의 오류가 한 응답에 함께 나오지 않을 수 있고(앞 오류를 고쳐 재제출하면 뒤 오류가 나온다), 같은 `field`에 오류가 둘 이상 올 수 있다. 클라이언트는 `errors[]`를 필드별로 모아 보여주되 이 순서 의존을 가정한다.

409의 `title`은 위 표의 코드(`DUPLICATE_REQUEST`, `NOT_MODIFIABLE`)를 쓴다.

## 이벤트 (내부, 알림 기능이 소비)

| 이벤트 | 필드 |
|--------|------|
| `GroupBuyChanged` | groupBuyId, 알림 대상 회원 id 목록(개설자 제외 참여자), 변경된 필드 이름 목록 |
| `GroupBuyCancelled` | groupBuyId, 알림 대상 회원 id 목록 |
| `GroupBuyCompleted` | groupBuyId, recipientIds(개설자 포함 모든 참여자). 수정으로 모집 완료가 될 때 |

커밋 후(AFTER_COMMIT)에만 전달된다. 대상이 없거나(참여자가 개설자뿐) 오픈채팅 링크만 바뀐 수정은 발행하지 않는다.
