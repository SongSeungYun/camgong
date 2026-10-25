# Data Model: 공구 개설

스키마는 `src/main/resources/schema.sql`에 둔다. 아래는 구조와 규칙이며 DDL 전문이 아니다.

## 엔티티

### Member (최소)

| 필드 | 설명 |
|------|------|
| id | PK |
| name | 표시 이름 |

가입·인증 필드는 가입 기능에서 확장한다.

### GroupBuy

| 필드 | 타입 | 규칙 | 근거 |
|------|------|------|------|
| id | BIGINT PK | | |
| organizer_id | FK → member | 개설자. 수정 불가 | FR-008 |
| title | VARCHAR(100) | 필수, 공백만 불가 | FR-003 |
| description | VARCHAR(2000) NULL | 선택 | FR-002 |
| item_name | VARCHAR(100) | 필수, 공백만 불가 | FR-003 |
| unit_price | INT | 1 ≤ n ≤ 1,000,000, 원 단위 (CHECK) | FR-006 |
| total_quantity | INT | 2 ≤ n ≤ 1000 | FR-005 |
| secured_quantity | INT | 1 ≤ n ≤ total_quantity (CHECK) | 결정 2, FR-013 |
| deadline | DATETIME(6) UTC | 개설 시 now < d ≤ created_at + 30일. 수정 시 단축 불가, 상한은 created_at + 30일 | FR-004, FR-012 |
| item_url | VARCHAR(500) | 필수, https 주소. 로그인한 모든 회원에게 공개 | FR-018 |
| open_chat_url | VARCHAR(500) | 필수, https 주소. 모집 완료 후 참여자에게만 공개 | FR-017 |
| status | VARCHAR(20) | OPEN / COMPLETED / CANCELLED | |
| version | BIGINT | JPA 버전 | 결정 4 |
| created_at, updated_at | DATETIME(6) | | |

필드 제약: `organizer_quantity`는 별도 컬럼이 아니라 개설자의 Participation 행이다.

### Participation

| 필드 | 타입 | 규칙 |
|------|------|------|
| id | BIGINT PK | |
| group_buy_id | FK → group_buy | |
| member_id | FK → member | |
| quantity | INT | > 0 (CHECK) |
| created_at | DATETIME(6) | |

`UNIQUE (group_buy_id, member_id)`: 한 회원은 한 공구에 한 행. 이 기능에서는 개설자 행만 만든다(FR-008). 참여/취소는 별도 기능이 같은 테이블을 쓴다.

### CreateGuard

| 필드 | 타입 | 규칙 |
|------|------|------|
| member_id | BIGINT | PK 일부 |
| fingerprint | CHAR(64) | PK 일부. 정규화한 입력값 전체의 SHA-256 hex |
| created_at | DATETIME(6) | 마지막으로 통과한 시각 |

도메인 엔티티가 아닌 중복 방지 장치다(결정 3). 공구와 FK 관계가 없다.

## 관계

- Member 1 — N GroupBuy (개설자)
- GroupBuy 1 — N Participation
- Member 1 — N Participation
- 불변식: `GroupBuy.secured_quantity = Σ Participation.quantity`

## 불변식과 지키는 곳

| 불변식 | 지키는 곳 |
|--------|-----------|
| 개설 직후 Participation 1행(개설자), secured = 개설자 수량 | 개설 트랜잭션 |
| secured ≤ total | 서비스 검증(락 안) + DB CHECK |
| secured = Σ quantity | 수량이 바뀌는 모든 쓰기가 같은 트랜잭션에서 둘 다 갱신 |
| COMPLETED는 secured = total일 때만 | `GroupBuy`의 상태 전이 메서드 하나가 판정 |
| 개설자 수량 ≥ 1, 개설 시 < total | 서비스 검증 |

## 상태 전이

```text
          수정/참여로 secured == total (FR-016)
  OPEN ─────────────────────────────────────────► COMPLETED (종료)
   │
   │ 개설자 취소 (마감 전, FR-014)
   └─────────────────────────────────────────────► CANCELLED (종료)
```

- 수정·취소 허용 조건은 `status = OPEN` **그리고** `deadline > now`(FR-015). 마감이 지났는데 아직 `OPEN`인 행은 이 기능에서 수정·취소할 수 없다. 이 행을 실패로 확정하는 것은 별도 기능이다.
- COMPLETED, CANCELLED에서는 나가는 전이가 없다.
- 개설 시점에는 개설자 수량 < 총 수량이므로 COMPLETED로 생성되지 않는다(FR-007).

## 수정 시 검증 순서 (락을 잡은 뒤)

1. 존재·개설자·`OPEN`·마감 전 확인 (404/403/409)
2. 단일 필드 형식은 요청 바인딩 단계에서 이미 통과했다고 본다
3. 마감: `new ≥ old` 그리고 `new ≤ created_at + 30일`
4. 수량: `organizerQuantity ≥ 1`, `secured' = secured − old개설자 수량 + new개설자 수량`, `secured' ≤ new total`
5. 반영 후 `secured' == total'`이면 `COMPLETED`로 전환
6. 변경된 필드 중 오픈채팅 링크 외(구매처 링크 포함)가 있고 개설자 외 참여자가 있으면 이벤트 발행
