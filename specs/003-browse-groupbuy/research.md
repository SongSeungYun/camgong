# Research: 공구 목록·상세 조회

NEEDS CLARIFICATION은 없다. 결정 1~3은 사용자가 직접 고른 것이고, 4 이후는 기본값이다(바꾸고 싶으면 알려줄 것).
헌법 III에 따라 각 결정은 구현 전에 사용자가 설명할 수 있어야 한다.

## 사용자 결정

### 1. N+1 전후 비교는 커밋 이력 + 쿼리 수 회귀 테스트

- **Decision**: 순진한 구현(지연 로딩 그대로)을 먼저 커밋하고 쿼리 수를 측정한다. 이어서 fetch join → batch size → DTO 프로젝션 순으로 단계마다 커밋·측정한다. 코드에는 최종 구현만 남고, 단계별 쿼리 수와 커밋 해시는 `docs/benchmarks/browse-n-plus-one.md`에 적는다. `QueryCountIT`가 최종 쿼리 수 상한을 회귀 테스트로 지킨다.
- **Rationale**: "개선 전후 쿼리 수를 기록한다"(헌법 II)가 말 그대로 이력으로 남는다. 조회 경로마다 구현체가 4개씩 생기는 004 방식보다 코드가 짧다.
- **Alternatives**: 설정 스위치로 모두 남김 — 재현은 쉽지만 경로마다 구현체가 늘어난다.
- **설명 체크**: 개선 후 코드만 남겨도 "전후 비교"가 증명되려면 문서에 무엇이 있어야 하는가?

### 2. 엔티티는 읽기 전용 연관관계를 Long id 필드와 병행

- **Decision**: 쓰기는 지금처럼 Long id 필드(`organizerId`, `groupBuyId`, `memberId`)로 한다. 같은 컬럼에 읽기 전용 `@ManyToOne(fetch = LAZY) @JoinColumn(name = "...", insertable = false, updatable = false)`를 하나 더 둔다.
  - `GroupBuy.organizer` → `Member`
  - `Participation.groupBuy` → `GroupBuy`, `Participation.member` → `Member`
- **Rationale**: 001·004의 쓰기 코드와 4방식 비교(락, 직접 `UPDATE`)를 건드리지 않고, 읽기에서 N+1이 실제로 생겨 개선 소재가 된다.
- **Alternatives**: 연관관계 완전 전환 — 모델은 JPA다워지지만 쓰기 경로와 4방식 비교에 영향이 있고 지연 로딩이 쓰기에 섞일 수 있다. 연관관계 없이 DTO 프로젝션만 — 가장 빠르지만 N+1이 애초에 없어 풀어서 설명할 소재가 사라진다.
- **설명 체크**: 같은 컬럼에 필드가 두 개일 때 읽기 전용 쪽의 `insertable=false, updatable=false`가 왜 필수인가?

### 3. 검색은 `LIKE` 부분 일치

- **Decision**: 검색어를 공백으로 나눠(최대 5단어) 단어마다 `(title LIKE :w ESCAPE '\\' OR item_name LIKE :w ESCAPE '\\')`를 만들고 `AND`로 묶는다. `:w`는 `%` + 이스케이프한 단어 + `%`. `%`, `_`, `\`는 이스케이프한다. MySQL 기본 콜레이션(`utf8mb4_0900_ai_ci`)이 대소문자를 구분하지 않는다.
- **Rationale**: 설정 없이 '햇'만으로 '햇반'을 찾는 요구(한 글자 부분 일치)를 만족한다. 스파이크에서 1만 건에서도 DB 시간이 1ms 안쪽이었다([docs/spikes.md](../../docs/spikes.md) #13). 인덱스를 타지 못해 필터 후 후보를 훑는 한계가 있다.
- **한계 (ponytail)**: 일치가 드문 검색어는 후보를 많이 훑는다(스파이크는 일치가 흔한 경우만 측정). 공구가 수만 건을 넘어 느려지면 `FULLTEXT`(ngram)나 검색용 컬럼으로 올린다.
- **Alternatives**: `FULLTEXT`(ngram 토큰 1) — 서버 설정과 인덱스 재구성이 필요하고 정렬·키셋과 조합이 복잡하다. 정규화 검색 컬럼 — 쓰기마다 갱신할 컬럼이 늘어 001을 건드려야 하고 이득이 작다.
- **설명 체크**: `LIKE '%단어%'`가 인덱스를 못 타는데도 이 규모에서 괜찮다고 판단한 근거는 무엇이고, 어느 시점에 바꿔야 하는가?

## 기본값 결정

### 4. 페이지네이션은 키셋(커서) 방식이고 전체 건수가 없다

- **Decision**: `size`(기본 20, 최대 50)보다 하나 더 조회(`size + 1`)해서 다음 페이지 유무를 판정하고 `nextCursor`를 돌려준다. 전체 건수 쿼리는 하지 않는다(spec FR-007).
  - 최신순: 정렬 키 `id DESC`, 커서 `id`. 다음 페이지 조건 `id < :cursorId`.
  - 마감 임박순: 정렬 키 `(deadline ASC, id ASC)`, 커서 `(deadline, id)`. 다음 조건 `(deadline > :d OR (deadline = :d AND id > :id))`.
  - 커서는 정렬·키 값을 담은 불투명 문자열(JSON → Base64URL)이다. 요청의 `sort`와 맞지 않으면 400.
- **Rationale**: 이어서 볼 때 이미 본 공구가 다시 나오거나 빠지지 않는다(오프셋은 중간에 새 공구가 생기면 어긋난다). 키셋은 깊은 페이지도 비용이 일정하다. 스파이크에서 인덱스를 타는 것을 확인했다(#13).
- **Alternatives**: 오프셋 — FR-007을 지키기 어렵다.
- **한계 (ponytail)**: 커서 이후에 생기거나 상태가 바뀐 공구는 그 순서 위치에 따라 이번 순회에 안 보일 수 있다. 다시 불러오면 반영된다(spec에 허용된 동작).
- **설명 체크**: 마감 임박순에서 `deadline`이 같은 공구가 여러 개일 때 `id`를 함께 쓰지 않으면 무슨 일이 생기는가?

### 5. 표시 상태는 서버가 계산한다

- **Decision**: 저장 상태와 마감 시각에서 `DisplayStatus`를 계산해 응답에 담는다(`Instant.now(clock)` 기준).

  | 표시 상태(API 코드) | 조건 |
  |--------------------|------|
  | `OPEN` (모집 중) | `status='OPEN'` 이고 `deadline > now` |
  | `PENDING_CLOSE` (마감됨, 확정 대기) | `status='OPEN'` 이고 `deadline <= now` |
  | `COMPLETED` (모집 완료) | `status='COMPLETED'` |
  | `FAILED` (모집 실패) | `status='FAILED'` |

  `CANCELLED`는 어디에도 노출하지 않는다. 상태 필터 `state` 파라미터는 위 네 값 중 하나이며 같은 조건을 쿼리에 쓴다. 기본값은 `OPEN`.
- **Rationale**: 프론트가 마감 판정을 따로 구현하지 않고 서버와 같은 시각 기준을 쓴다(spec FR-004, FR-015).

### 6. 동적 조건은 JPA Criteria API로 짠다

- **Decision**: 상태, 카테고리, 검색 단어, 커서 조건을 `GroupBuySearchRepository`에서 Criteria API로 조립하고 `setMaxResults(size + 1)`로 조회한다. 목록 카드는 DTO 프로젝션으로 필요한 컬럼만 읽는다.
- **Rationale**: 새 의존성(QueryDSL 등) 없이 조건이 가변인 쿼리를 만든다.

### 7. 상세는 쿼리 수가 데이터 양과 무관해야 한다

- **Decision**: 최종 쿼리 수 상한은 **개설자 외 요청 3개 이하, 개설자 요청 4개 이하**다.
  1. 공구 + 개설자 이름 (`join fetch organizer`)
  2. 내 참여 (공구, 회원)
  3. 참여 인원 수 (`COUNT`)
  4. (개설자만) 명단: `participation join fetch member` where 공구
- **링크·명단 공개**: 오픈채팅 링크는 spec FR-011, 명단은 FR-012 규칙으로 서비스가 필터링한다(DB에서는 모두 읽어도 응답에서 가린다 — 명단 쿼리 자체를 개설자일 때만 실행한다).
- **N+1 시나리오 B**: 순진한 구현은 명단을 돌며 `participation.getMember().getName()`을 불러 참여자 수만큼 쿼리가 생긴다.

### 8. 내 활동

- **Decision**: 두 탭 모두 공구 개설 시각이 늦은 순(`group_buy.id DESC`)의 키셋이다. '내가 연 공구'는 `organizer_id = me`, '내가 참여한 공구'는 `participation.member_id = me`를 조인해 공구를 읽는다(내가 연 공구도 포함, spec). `CANCELLED`는 제외.
- **N+1 시나리오 A**: 순진한 구현은 참여 목록을 읽고 항목마다 `participation.getGroupBuy()`로 공구를 지연 로딩한다. 한 페이지 N건이면 `1 + N` 쿼리.

### 9. 인덱스

- **Decision**: `participation (member_id, group_buy_id)`(내가 참여한 공구), `group_buy (organizer_id, id)`(내가 연 공구)를 `schema.sql`에 추가한다. 005가 추가한 `group_buy (status, deadline)`을 마감 임박순에 쓴다. 최신순은 PK 역방향 스캔에 필터를 건다. 카테고리용 인덱스는 두지 않는다.
- **Rationale**: 스파이크에서 이 접근으로 1만 건에서 쿼리당 1ms 안쪽이었다(#13). 카테고리는 선택도가 낮아 인덱스 이득이 작다.

### 10. 쿼리 수 측정 도구

- **Decision**: Hibernate `StatementInspector`로 요청 단위(스레드 로컬)로 실행된 SQL 개수를 센다. 테스트 프로파일에서만 켜고 `QueryCountIT`가 사용한다. 새 의존성이 없다.
- **한계**: JPA/Hibernate를 거친 쿼리만 센다. `JdbcClient`로 나가는 쿼리(001 중복 가드 등)는 세지 않는다. 조회 경로는 JPA만 쓴다.

### 11. N+1 측정 절차 (헌법 II)

| 시나리오 | 데이터 | 순진한 구현의 예상 | 최종 목표 |
|----------|--------|--------------------|-----------|
| A 내가 참여한 공구 한 페이지 | 참여 공구 N = 1, 20, 50건 | 1 + N 쿼리 | N과 무관하게 2 이하 |
| B 상세의 개설자용 명단 | 참여자 N = 1, 20, 50명 | 쿼리가 N에 비례 | N과 무관하게 4 이하 |

- 개선 단계: ① 순진한 지연 로딩 → ② fetch join → ③ `hibernate.default_batch_fetch_size` → ④ DTO 프로젝션. 단계마다 N별 쿼리 수를 표로 기록하고 각 커밋 해시를 적는다.
- 응답 시간(SC-001, SC-007)은 공구 100건과 1만 건 시드로 목록 첫 페이지를 k6로 측정해 같은 문서에 적는다.
- **설명 체크**: fetch join과 batch size는 같은 N+1을 각각 어떻게 줄이고, 어떤 경우에 batch size가 더 나은가?

### 12. 로그인 처리

- **Decision**: 목록은 `X-Member-Id` 없이도 동작한다(있어도 쓰지 않는다). 상세와 내 활동은 필수이며 없으면 401. 로그인 후 메인으로 돌아가는 동작은 프론트와 002의 몫이다.

### 13. 시드 데이터

- **Decision**: 성능 측정용으로 공구 100건 / 1만 건(상태·카테고리·마감 시각 분산)을 만드는 SQL을 `load/seed-browse.sql`에 둔다. 한글 제목에는 흔한 단어와 드문 단어를 섞어 검색 최악 사례도 잰다.

## 미검증 가정 (구현 시작 직전에 확인)

| # | 가정 | 확인 방법 |
|---|------|-----------|
| 1 | Hibernate `StatementInspector`가 지연 로딩·fetch join 쿼리를 모두 센다 | 단위 시험 |
| 2 | Criteria API의 `LIKE ... ESCAPE`가 MySQL에서 의도대로 이스케이프한다 | 특수문자 검색 통합 테스트 |
| 3 | 일치가 드문 검색어의 1만 건 전체 스캔 비용이 SC-001(1초) 안에 든다 | 시드 후 k6 |
| 4 | `default_batch_fetch_size`가 명단·내 활동에서 기대한 `IN` 쿼리로 나간다 | SQL 로그 |
