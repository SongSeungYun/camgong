# Data Model: 공구 목록·상세 조회

**새 테이블은 없다.** 읽기 전용 기능이라 쓰기 규칙은 없고, 엔티티 매핑 변경, 인덱스, 표시 상태 계산, 쿼리 모양을 정한다.

## 엔티티 매핑 변경 (결정 2)

쓰기용 Long id 필드는 그대로 두고, 같은 컬럼에 읽기 전용 연관관계를 더한다.

| 엔티티 | 읽기 전용 연관관계 | 매핑 |
|--------|--------------------|------|
| `GroupBuy` | `organizer` → `Member` | `@ManyToOne(fetch = LAZY) @JoinColumn(name = "organizer_id", insertable = false, updatable = false)` |
| `Participation` | `groupBuy` → `GroupBuy` | `@ManyToOne(fetch = LAZY) @JoinColumn(name = "group_buy_id", insertable = false, updatable = false)` |
| `Participation` | `member` → `Member` | `@ManyToOne(fetch = LAZY) @JoinColumn(name = "member_id", insertable = false, updatable = false)` |

- 읽기 전용 연관관계는 쓰기 경로(001, 004, 005)에서 접근하지 않는다.
- 연관관계는 `LAZY`다. 순진한 구현에서 N+1이 생기는 지점이고, 최종 구현은 fetch join·batch·프로젝션으로 푼다.

## 인덱스 추가

| 인덱스 | 용도 | 파일 |
|--------|------|------|
| `participation (member_id, group_buy_id)` | 내가 참여한 공구, 공구 id 역순 키셋 | `schema.sql` |
| `group_buy (organizer_id, id)` | 내가 연 공구 키셋 | `schema.sql` |

이미 있는 것: `group_buy (status, deadline)`(005)은 마감 임박순 키셋, PK는 최신순, `participation UNIQUE (group_buy_id, member_id)`는 상세의 내 참여 조회와 명단에 쓴다.

## 표시 상태 (결정 5)

| API 코드 | 의미 | 조건 |
|----------|------|------|
| `OPEN` | 모집 중 | `status='OPEN' AND deadline > now` |
| `PENDING_CLOSE` | 마감됨(확정 대기) | `status='OPEN' AND deadline <= now` |
| `COMPLETED` | 모집 완료 | `status='COMPLETED'` |
| `FAILED` | 모집 실패 | `status='FAILED'` |

`CANCELLED`는 노출하지 않는다(어떤 조회에서도 `status <> 'CANCELLED'`). 같은 조건이 `status` 필터에 쓰인다.

## 쿼리 모양

목록 (필터는 선택, `:now`는 앱 `Clock`):

```sql
SELECT id, title, item_name, category, unit_price, secured_quantity, total_quantity, deadline, status
FROM group_buy
WHERE <status 조건>
  [AND category = :category]
  [AND (title LIKE :w1 ESCAPE '\' OR item_name LIKE :w1 ESCAPE '\')  -- 단어마다 AND, 최대 5개]
  [AND <키셋 조건>]
ORDER BY <id DESC | deadline, id>
LIMIT :size + 1
```

상세 (결정 7, 최종 쿼리 수 ≤ 4):

```sql
-- 1) 공구 + 개설자 이름
SELECT g.*, m.name FROM group_buy g JOIN member m ON m.id = g.organizer_id
WHERE g.id = :id AND g.status <> 'CANCELLED'
-- 2) 내 참여
SELECT quantity FROM participation WHERE group_buy_id = :id AND member_id = :me
-- 3) 참여 인원 수
SELECT COUNT(*) FROM participation WHERE group_buy_id = :id
-- 4) 개설자만: 명단
SELECT p.quantity, m.id, m.name FROM participation p JOIN member m ON m.id = p.member_id
WHERE p.group_buy_id = :id ORDER BY p.id
```

내가 참여한 공구:

```sql
SELECT g.<카드 컬럼>, p.quantity
FROM participation p JOIN group_buy g ON g.id = p.group_buy_id
WHERE p.member_id = :me AND g.status <> 'CANCELLED' [AND g.id < :cursor]
ORDER BY g.id DESC LIMIT :size + 1
```

내가 연 공구는 `group_buy WHERE organizer_id = :me AND status <> 'CANCELLED' [AND id < :cursor] ORDER BY id DESC`.

## 공개 규칙 (spec FR-011, FR-012)

| 필드 | 보이는 대상 |
|------|-------------|
| `openChatUrl` | 개설자: `OPEN`·`PENDING_CLOSE`·`COMPLETED` 상태에서 항상. 참여자(개설자 외): `COMPLETED`일 때만. 그 외(참여하지 않은 회원, `FAILED`, `PENDING_CLOSE`의 참여자): 숨김 |
| 참여자 명단 | 개설자만. 그 외에는 `participantCount`와 수량 합(= `securedQuantity`)만 |
| 개설자 이름, 구매처 링크 | 상세를 볼 수 있는 모든 로그인 회원 |

숨기는 필드는 `null`로 내려보낸다(필드가 있고 값이 없음). 개설자의 `PENDING_CLOSE`에서도 링크가 보이는 것은 001 FR-017의 개설자 규칙을 따른 것이다.

## 불변식

[domain.md](../../docs/domain.md) 4절 중 이 기능이 책임지는 것.

| # | 불변식 | 지키는 곳 |
|---|--------|-----------|
| I8 | 오픈채팅 링크는 허용된 대상에게만 | 조회 서비스(위 표) |
| I9 | 참여자 명단은 개설자에게만 | 조회 서비스, 명단 쿼리 자체를 개설자일 때만 실행 |
| (003) | `CANCELLED`는 어디에도 노출하지 않는다 | 모든 조회 쿼리의 `status <> 'CANCELLED'` |
| (003) | 상세·내 활동의 쿼리 수는 데이터 양과 무관 | `QueryCountIT` 회귀 테스트 |
