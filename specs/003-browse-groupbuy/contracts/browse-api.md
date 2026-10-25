# API Contract: 공구 목록·상세 조회

읽기 전용. 요청·응답은 JSON, 시간은 오프셋 포함 ISO-8601. `X-Member-Id` 헤더는 임시 인증이다(002에서 교체).
오류 본문은 001과 같은 `ProblemDetail` + `errors[]`.

## GET /api/groupbuys — 목록

**인증**: 불필요(로그인 없이 누구나). 헤더가 있어도 쓰지 않는다.

**쿼리 파라미터**

| 이름 | 값 | 기본 | 설명 |
|------|-----|------|------|
| `state` | `OPEN`, `PENDING_CLOSE`, `COMPLETED`, `FAILED` | `OPEN` | 표시 상태 필터. 하나만 |
| `category` | `FOOD`, `DAILY`, `KITCHEN`, `HEALTH_BEAUTY`, `STATIONERY`, `ETC` | 전체 | 카테고리 필터 |
| `q` | 문자열 | 없음 | 검색어. 공백으로 나눈 단어(최대 5개)가 모두 제목 또는 상품명에 부분 일치. 앞뒤 공백 무시, 50자 이하. 비어 있으면 검색 안 함 |
| `sort` | `latest`, `deadline` | `latest` | 최신순(개설 시각 늦은 순) / 마감 임박순(마감 시각 이른 순) |
| `cursor` | 문자열 | 없음 | 이전 응답의 `nextCursor`. 같은 `sort`·필터로만 유효 |
| `size` | 1~50 | 20 | 한 번에 받을 개수 |

**성공** `200 OK`

```json
{
  "items": [
    {
      "id": 1,
      "title": "햇반 24개 공구",
      "itemName": "햇반 210g",
      "category": "FOOD",
      "unitPrice": 1200,
      "securedQuantity": 9,
      "totalQuantity": 24,
      "deadline": "2026-11-01T18:00:00+09:00",
      "state": "OPEN"
    }
  ],
  "nextCursor": "eyJ..."
}
```

- 다음 페이지가 없으면 `nextCursor`는 `null`이다. 전체 건수는 주지 않는다.
- 결과가 없으면 `items: []`이다(오류가 아니다). 검색 결과 없음과 빈 목록의 안내 구분은 화면이 `q` 유무로 한다.
- 카드에는 개설자 정보와 구매처 링크가 없다.

| 상태 | 조건 |
|------|------|
| 400 | 지원하지 않는 `state`·`category`·`sort`, `size` 범위 밖, `q`가 50자 초과, 커서가 잘못되었거나 `sort`와 맞지 않음 |

## GET /api/groupbuys/{id} — 상세

**인증**: 필수. 없거나 존재하지 않는 회원이면 `401`.

**성공** `200 OK`

```json
{
  "id": 1,
  "title": "햇반 24개 공구",
  "description": null,
  "itemName": "햇반 210g",
  "category": "FOOD",
  "itemUrl": "https://example.com/item/123",
  "unitPrice": 1200,
  "totalQuantity": 24,
  "securedQuantity": 15,
  "remainingQuantity": 9,
  "deadline": "2026-11-01T18:00:00+09:00",
  "state": "OPEN",
  "organizerName": "홍길동",
  "isOrganizer": false,
  "participantCount": 2,
  "myParticipation": { "quantity": 6, "amount": 7200 },
  "openChatUrl": null,
  "participants": null,
  "createdAt": "2026-10-25T07:30:00+09:00"
}
```

- `myParticipation`: 참여하지 않았으면 `null`(화면은 '참여 안 함'). `amount = unitPrice × quantity`.
- `participantCount`: 개설자를 포함한 참여자 수.
- `openChatUrl`: data-model.md 공개 규칙. 숨기면 `null`.
- `participants`: 개설자에게만 `[{ "memberId": 1, "name": "홍길동", "quantity": 9 }, ...]`, 그 외에는 `null`.
- 001의 최소 상세 `GET`을 이 응답이 대체한다(경로 동일, 필드 확장). `organizerId` 대신 `organizerName`·`isOrganizer`를 쓴다.

| 상태 | 조건 |
|------|------|
| 401 | 회원 식별 실패 |
| 404 | 공구가 없거나 취소됨 |

## GET /api/me/groupbuys — 내 활동

**인증**: 필수.

| 이름 | 값 | 기본 | 설명 |
|------|-----|------|------|
| `role` | `organizer`, `participant` | 필수 | `organizer`: 내가 연 공구, `participant`: 내가 참여한 공구(내가 연 공구 포함) |
| `cursor`, `size` | 목록과 같다 | | 정렬은 공구 개설 시각이 늦은 순 고정 |

**성공** `200 OK`: `items`의 항목은 목록 카드와 같고 `role=participant`일 때 `myQuantity`가 추가된다. `nextCursor`는 목록과 같다. 취소된 공구는 나오지 않는다.

| 상태 | 조건 |
|------|------|
| 400 | `role` 누락·잘못된 값, `size` 범위 밖, 잘못된 커서 |
| 401 | 회원 식별 실패 |
