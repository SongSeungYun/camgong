# Data Model: 알림

> 초안: 추천안 기준([research.md](research.md), 2026-10-26 확정).

## 새 테이블 `notification`

| 컬럼 | 타입 | 규칙 | 근거 |
|------|------|------|------|
| `id` | `BIGINT AUTO_INCREMENT PRIMARY KEY` | | |
| `event_id` | `CHAR(36)` | 필수. 이벤트의 UUID | 결정 2, FR-005 |
| `recipient_id` | `BIGINT` | 필수. 받는 회원 id (의도적으로 FK 없음: 대량 삽입 비용과 회원 삭제 기능이 없음. 구현은 FK를 두지 않는다) | FR-002 |
| `type` | `VARCHAR(30)` | 필수. `GROUPBUY_COMPLETED`, `GROUPBUY_FAILED`, `GROUPBUY_CHANGED`, `GROUPBUY_CANCELLED` | FR-001 |
| `group_buy_id` | `BIGINT` | 필수. FK는 두지 않는다(취소된 공구도 알림이 남는다) | |
| `group_buy_title` | `VARCHAR(100)` | 필수. 사건 시점의 제목 스냅샷 | FR-003, 결정 7 |
| `changed_fields` | `VARCHAR(200)` | `GROUPBUY_CHANGED`일 때만. 바뀐 항목 이름을 쉼표로 이은 텍스트 | FR-003 |
| `created_at` | `DATETIME(6)` | 필수. 앱 `Clock` | FR-003 |
| `read_at` | `DATETIME(6)` | NULL = 읽지 않음 | FR-006 |

제약과 인덱스:

- `UNIQUE (event_id, recipient_id)` — 한 사건에 한 대상자는 한 번만 (FR-005).
- `KEY (recipient_id, id)` — 목록 키셋 (`recipient_id = ? ORDER BY id DESC`).
- `KEY (recipient_id, read_at)` — 읽지 않은 개수.
- `KEY (created_at)` — 30일 정리.

`schema.sql`의 `CREATE TABLE IF NOT EXISTS` 정의 안에 `KEY` 절로 넣는다(MySQL은 `CREATE INDEX IF NOT EXISTS`를 지원하지 않는 것으로 알고 있다. 구현 시 확인).

## 이벤트 변경 (교차)

| 이벤트 | 추가 필드 | 비고 |
|--------|-----------|------|
| `GroupBuyChanged` | `eventId`, `groupBuyTitle` | 수정 후 제목(스냅샷) |
| `GroupBuyCancelled` | `eventId`, `groupBuyTitle` | 취소된 공구는 읽을 수 없으니 제목이 필요 |
| `GroupBuyCompleted` | `eventId`, `groupBuyTitle` | |
| `GroupBuyFailed` | `eventId`, `groupBuyTitle` | |

`recipientIds`는 이미 들어 있다(개설자 포함 여부는 이벤트별 규칙을 그대로 따른다). 이벤트를 만드는 쪽이 사건 시점의 참여자 목록으로 채우므로 FR-002("사건 시점에 참여 기록이 있는 회원")가 자연히 지켜진다.

## 불변식

| # | 불변식 | 지키는 곳 |
|---|--------|-----------|
| (007) | 한 사건의 한 대상자는 알림 하나 | `UNIQUE (event_id, recipient_id)` |
| (007) | 알림의 수신자는 이벤트의 `recipientIds`뿐 | 리스너가 이벤트 값만 사용 |
| (007) | 알림에는 다른 회원의 정보가 없다 | 컬럼 구성(제목·종류·바뀐 항목 이름·시각뿐) |
| (007) | 회원은 자기 알림만 읽는다 | 모든 조회·읽음 쿼리에 `recipient_id = :me` |
| (007) | 롤백된 변경에는 알림이 없다 | `AFTER_COMMIT`에서만 이벤트를 받는다 |

## 쓰는 쿼리

생성 (청크, 중복 무시):

```sql
INSERT INTO notification (event_id, recipient_id, type, group_buy_id, group_buy_title, changed_fields, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE id = id
```

목록 (키셋):

```sql
SELECT id, type, group_buy_id, group_buy_title, changed_fields, created_at, read_at
FROM notification
WHERE recipient_id = :me [AND id < :cursor]
ORDER BY id DESC LIMIT :size + 1
```

읽지 않은 개수: `SELECT COUNT(*) FROM notification WHERE recipient_id = :me AND read_at IS NULL`

읽음 처리 (멱등, 본인 것만):

```sql
UPDATE notification SET read_at = COALESCE(read_at, :now)
WHERE id = :id AND recipient_id = :me
```

영향 행이 0이면 서비스가 먼저 `SELECT`로 존재와 소유를 확인해 404와 "이미 읽음"을 구분한다.

정리:

```sql
DELETE FROM notification WHERE created_at < :now - INTERVAL 30 DAY LIMIT 1000
```

한 번에 끝나지 않으면 영향 행이 0이 될 때까지 반복한다.
