# API Contract: 알림

> 초안: 추천안 기준(사용자 결정 대기, [research.md](../research.md) 결정 5·6·7).

요청·응답은 JSON, 시간은 오프셋 포함 ISO-8601. 모든 요청에 `X-Member-Id` 헤더(임시 인증, 002에서 교체). 오류 본문은 001과 같은 `ProblemDetail` + `errors[]`. 회원은 자기 알림만 볼 수 있다.

## GET /api/notifications — 알림 목록

**인증**: 필수. 없으면 `401`.

| 쿼리 | 값 | 기본 | 설명 |
|------|-----|------|------|
| `cursor` | 문자열 | 없음 | 이전 응답의 `nextCursor` (003과 같은 불투명 커서) |
| `size` | 1~50 | 20 | |

**성공** `200 OK` — 최근 순(`id DESC`), 전체 건수는 주지 않는다.

```json
{
  "items": [
    {
      "id": 31,
      "type": "GROUPBUY_COMPLETED",
      "groupBuyId": 1,
      "groupBuyTitle": "햇반 24개 공구",
      "changedFields": null,
      "createdAt": "2026-10-27T10:00:00+09:00",
      "read": false,
      "linkable": true
    },
    {
      "id": 30,
      "type": "GROUPBUY_CHANGED",
      "groupBuyId": 2,
      "groupBuyTitle": "물티슈 공구",
      "changedFields": ["unitPrice", "deadline"],
      "createdAt": "2026-10-27T09:30:00+09:00",
      "read": true,
      "linkable": true
    }
  ],
  "nextCursor": null
}
```

- `type`: `GROUPBUY_COMPLETED`, `GROUPBUY_FAILED`, `GROUPBUY_CHANGED`, `GROUPBUY_CANCELLED`.
- `changedFields`: `GROUPBUY_CHANGED`일 때만 항목 이름 배열(`title`, `description`, `itemName`, `category`, `itemUrl`, `unitPrice`, `totalQuantity`, `deadline`, `organizerQuantity`). 값(이전·새)은 담지 않는다.
- `linkable`: 공구 상세로 이동할 수 있는지. `GROUPBUY_CANCELLED`는 `false`(취소된 공구는 열 수 없고 알림 내용만 보인다).
- 다른 회원의 정보(이름, 수량, 링크, 이메일)는 어떤 필드에도 없다.

| 상태 | 조건 |
|------|------|
| 400 | `size` 범위 밖, 잘못된 커서 |
| 401 | 회원 식별 실패 |

## GET /api/notifications/unread-count — 읽지 않은 개수

**인증**: 필수. **성공** `200 OK` `{ "unreadCount": 3 }`.

## PUT /api/notifications/{id}/read — 읽음 처리

본문 없음. 멱등: 이미 읽은 알림이어도 `200`이고 읽은 시각은 바뀌지 않는다.

**성공** `200 OK` — 갱신된 알림 항목(위 `items`의 항목 모양).

| 상태 | 조건 |
|------|------|
| 401 | 회원 식별 실패 |
| 404 | 알림이 없거나 **다른 회원의 알림**(소유 여부를 알려주지 않기 위해 같은 응답) |

## 내부 계약: 이벤트 소비

`NotificationEventListener`는 아래 이벤트를 `AFTER_COMMIT`에서 비동기로 받는다. 이벤트 형식은 각 기능의 계약에 있고, 007은 `eventId`, `groupBuyTitle`을 요구한다(교차 변경, [data-model.md](../data-model.md)).

| 이벤트 | 알림 `type` | 대상 |
|--------|-------------|------|
| `GroupBuyCompleted` | `GROUPBUY_COMPLETED` | `recipientIds` (개설자 포함 전원) |
| `GroupBuyFailed` | `GROUPBUY_FAILED` | `recipientIds` (개설자 포함 전원) |
| `GroupBuyChanged` | `GROUPBUY_CHANGED` | `recipientIds` (개설자 제외 참여자) |
| `GroupBuyCancelled` | `GROUPBUY_CANCELLED` | `recipientIds` (개설자 제외 참여자) |

대상이 비어 있으면 알림을 만들지 않는다.

## 설정

| 키 | 기본값 | 뜻 |
|----|--------|-----|
| `camgong.notification.async.core-size` / `max-size` / `queue-capacity` | `2` / `4` / `1000` | 알림 전용 실행기 |
| `camgong.notification.insert-chunk-size` | `500` | 배치 삽입 청크 |
| `camgong.notification.retry-attempts` | `3` | 생성 재시도 횟수 |
| `camgong.notification.retention` | `30d` | 보관 기간 |
| `camgong.notification.cleanup.enabled` / `interval` | `true` / `1d` | 정리 작업 |
