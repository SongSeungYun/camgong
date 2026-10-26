# 가정 검증 스파이크

설계가 기대는 가정을 버릴 코드로 확인한 기록이다. 코드는 저장소에 두지 않는다(임시 MySQL 컨테이너 + 단일 파일 Java).

**환경**: MySQL 8.4.13, Connector/J 9.1.0, 2026-10-26

## 검증 완료 (DB 수준)

| # | 가정 | 방법 | 결과 | 설계 반영 |
|---|------|------|------|-----------|
| 1 | `INSERT ... ON DUPLICATE KEY UPDATE`의 영향 행 수로 신규/중복/만료 갱신을 구분할 수 있다 | 같은 키를 t+0초, +1초, +11초, +12초에 연속 호출 | `useAffectedRows=true`: 1, 0, 2, 0. **기본값(false)은 1, 1, 2, 1** — 중복도 1이라 신규와 구분 불가 | JDBC URL에 `useAffectedRows=true` 필수 (T003, T004). 분석 보고서 R1이 맞았다 |
| 2 | 동시 요청 중 하나만 통과한다 | 연결 2개: 첫 요청 미커밋 상태에서 둘째 요청 | 둘째가 첫 요청이 끝날 때까지 대기. 첫 요청 커밋 후 결과 **0(중복)**, 롤백 후 결과 **1(신규)** | 결정 3이 성립. 검증 실패로 롤백하면 가드도 풀린다 |
| 3 | 행 별칭 문법(`AS new`)이 MySQL 8.4에서 동작한다 | 위 SQL에 사용 | **그대로는 오류**: `Column 'created_at' in field list is ambiguous`. 기존 값은 `create_guard.created_at`처럼 테이블 이름을 붙여야 한다 | research.md와 T016의 SQL을 수정함 |
| 4 | DB `CHECK`가 INSERT·UPDATE 모두에서 초과를 막는다 | 확보 > 총, 확보 0, 가격 1,000,001 삽입, 그리고 UPDATE로 확보 > 총·총 < 확보 | 전부 거부(오류 3819) | 결정 2의 최후 방어선이 성립 |
| 5 | `SELECT ... FOR UPDATE`가 같은 행 요청을 직렬화한다 | 연결 2개 | 둘째가 첫 커밋까지 대기(약 1.5초 뒤 해제) | 결정 4가 성립 |
| 10 | 한 `UPDATE` 안에서 수량을 더하고 그 값으로 상태를 바꿀 수 있다(`SET` 평가 순서) | 총 24·확보 9에서 +6, +9 | MySQL은 `SET`을 왼쪽에서 오른쪽으로 평가한다. +6은 `OPEN` 유지, +9로 24가 되면 같은 문장에서 `COMPLETED`가 됐다 | 004 `conditional` 방식의 갱신문이 성립 |
| 12 | `SELECT ... FOR UPDATE SKIP LOCKED`로 공구를 선점하면 다른 연결은 기다리지 않고 건너뛴다 | 연결 2개: 첫 연결이 후보 3건 선점, 둘째가 같은 쿼리·단건 선점, 이후 첫 연결이 `FAILED`로 바꾸고 커밋 | 둘째는 0ms에 잠기지 않은 행(4, 5)만 받고, 잠긴 id=1은 빈 결과. 커밋 뒤에는 확정된 행이 `status='OPEN'` 조건에서 사라진다 | 005 중복 방지(결정 1)가 성립 |
| 13 | 키셋 페이지네이션이 인덱스를 타고, `LIKE` 부분 일치 검색이 1만 건에서도 충분히 빠르다 | 공구 100건·1만 건 테이블에서 모집 중 최신순/마감 임박순 첫 페이지와 다음 페이지, 검색 '햇'과 '햇반 24'(AND)의 시간과 `EXPLAIN` | 1만 건에서 모든 쿼리가 DB 시간 기준 1ms 안쪽(0.11~0.64ms). 최신순은 PK 역방향 스캔, 마감 임박순은 `(status, deadline)` 인덱스 범위, 검색은 PK 역방향 스캔에 `LIMIT`로 조기 종료. 100건 대비 1만 건 DB 시간 증가가 2배 안쪽. **한계**: DB 시간만 쟀고 앱·HTTP·ORM 오버헤드와 일치가 드문 검색어(전체 스캔)는 재지 않았다 | 003 plan의 키셋·LIKE 결정이 성립. 드문 검색어는 구현 후 k6로 재측정 |
| 11 | `WHERE` 조건(초과, 종료 상태)이 영향 행 0으로 드러나고, 변화량 0도 `version` 증가로 영향 행 1이다 | +16(초과), 종료 상태에서 +1, +0 | 초과·`COMPLETED`는 0행, 변화량 0은 `version`이 올라 1행(`useAffectedRows=true`) | 004의 거부 판정과 no-op 변경 처리가 성립 |

## 미검증 (Spring 수준, 구현 시작 직전에 확인)

| # | 가정 | 확인 방법 |
|---|------|-----------|
| 6 | `schema.sql`이 `ddl-auto=validate`보다 먼저 실행된다 | Spring Boot 최소 프로젝트로 기동 |
| 7 | `@DynamicPropertySource`로 준 JDBC URL 옵션(`useAffectedRows`)이 실제로 적용된다 | 테스트에서 가드 영향 행 수 단언 (T012) |
| 8 | JPA `PESSIMISTIC_WRITE`가 `FOR UPDATE`로 나가고 `@Version`과 같이 동작한다 | SQL 로그 확인 |
| 9 | `Instant` ↔ `DATETIME(6)`가 UTC로 저장되고 `MutableClock` 기준 비교가 맞다 | 경계 테스트 (T020) |


## Spring 수준 검증 (JDK 25, Boot 4.1.2) — 밤샘 작성

**환경**: Spring Boot 4.1.2 (시도 1회 만에 성공, 시도 기록: 정식 최신 4.1.2 선택, `latest`인 4.2.0-RC1은 제외), Hibernate 7.4.13, Spring Data JPA 4.1.2, MySQL Connector/J 9.7.0, Testcontainers 2.0.5 + MySQL 8.4, **JDK 25로 검증**(프로젝트 목표는 Java 21이므로 Java 21에서는 한 번 더 돌려야 한다), JVM 기본 시간대를 `Asia/Seoul`로 두고 실행(UTC 저장 검증용). 스파이크 코드는 저장소에 없다(스크래치패드의 임시 프로젝트, 테스트 13개 모두 통과). 컨테이너 `camgong-spike-mysql`은 정리했다.

| # | 가정 (출처) | 판정 | 증거 / 영향 |
|---|------------|------|-------------|
| 6 | `schema.sql`이 `ddl-auto=validate`보다 먼저 실행된다 | **확인됨** | `spring.sql.init.mode=always` + `ddl-auto=validate`로 빈 DB에서 컨텍스트가 정상 기동, 테이블 4개가 `schema.sql`에서만 생성됨. (재기동 시 `IF NOT EXISTS` 재실행은 이번에 따로 확인 안 함 → 확인 못 함) |
| 7 | `@DynamicPropertySource`의 `useAffectedRows=true`가 적용되어 가드 영향 행 수가 구분된다 | **확인됨** | 신규 삽입 1, 같은 값 중복 0, 값이 바뀐 중복 2. 001 중복 가드의 해석(0/1/2)이 성립. 행 별칭 문법(`AS new … create_guard.token`)도 동작 |
| 8 | `PESSIMISTIC_WRITE` → `FOR UPDATE`, `@Version`과 동작 | **확인됨** | 로그: `select … where id=? for update of gb1_0`, 이어서 `update … where id=? and version=?`. 락 조회 뒤 변경하면 버전이 1 증가 |
| 9 | `Instant` ↔ `DATETIME(6)` UTC 저장, `MutableClock` 비교 | **확인됨** | JVM 시간대가 Asia/Seoul이어도 DB 원문 `2026-10-26 01:01:00.123456`(UTC), 읽으면 같은 `Instant`(마이크로초 유지). `hibernate.jdbc.time_zone=UTC` 필요. 시계를 61초 옮기면 후보 조회(cutoff = now−1s)에 잡힘 |
| 5 | 네이티브 `SELECT … FOR UPDATE SKIP LOCKED`가 영속 엔티티로 돌아온다 (005) | **확인됨** | `em.contains(...) == true`, 상태를 바꾸면 커밋 때 `UPDATE`가 나감. 다른 트랜잭션이 잠근 행은 건너뛰고 다음 행을 받음 |
| 5' | (**새 발견**) SKIP LOCKED 워커 둘이 동시에 처리·커밋해도 서로 막지 않는다 (005) | **틀림 (기본 격리 수준에서)** | **REPEATABLE READ(MySQL 기본)**: 첫 트랜잭션이 a를 잠근 채 열려 있으면, 둘째 트랜잭션이 b를 받아 `status`를 `OPEN→COMPLETED`로 바꾸는 `UPDATE`가 **락 대기 시간 초과**(5초로 낮춘 `innodb_lock_wait_timeout`)로 실패. **READ COMMITTED**: 같은 시나리오에서 둘째가 b를 처리하고 첫째가 열린 채로 커밋 성공. 원인은 범위 조회의 갭/넥스트키 락이 `(status, deadline)` 인덱스 위치를 막아서라고 **추정**(락 상태를 직접 들여다보지는 않음). 영향: **005 research/plan의 "SKIP LOCKED로 워커 간 충돌 없음"은 격리 수준 지정 없이는 성립하지 않음**. 후보: (A) 스케줄러 트랜잭션만 READ COMMITTED, (B) 후보 id만 SKIP LOCKED로 뽑고 건별로 짧게 처리, (C) 단일 워커 + 건별 처리. **상태: 사용자 결정 대기** — 추천 A. 데이터가 2행인 최소 재현이라 실제 규모에서의 빈도는 확인 못 함 |
| — | 다른 빈의 `@Transactional`은 건마다 새 트랜잭션, 같은 클래스 호출은 아님 (005) | **확인됨** | 스케줄러(트랜잭션 없음)가 다른 빈 `processOne`을 3번 호출: 각 호출 `active=true`, 둘째가 예외로 롤백돼도 첫째·셋째는 `COMPLETED`로 남음(`[COMPLETED, OPEN, COMPLETED]`). 같은 클래스 안 `outer()→inner()` 호출은 `active=false`(프록시 우회) |
| — | 후보 조회가 인덱스 `(status, deadline)`을 탄다 (005) | **확인됨** (조건부) | 3,000행(OPEN 10%)에서 `EXPLAIN`: `type=range, key=idx_status_deadline, rows=1`. 행이 훨씬 적을 때는 옵티마이저가 풀스캔을 고를 수 있어 **측정은 단계 3에서** |
| — | `StatementInspector`가 지연 로딩·fetch join 쿼리를 센다 (003) | **확인됨** | 참여 25건의 회원 이름을 지연 로딩하면 SQL 4개, fetch join이면 1개를 셈. `hibernate.session_factory.statement_inspector`로 등록 |
| — | `default_batch_fetch_size`가 `IN` 쿼리로 나간다 (003) | **확인됨** | 설정값 10에 회원 25명 → `select … where id in (?×10)`이 3번(총 1+3). N+1이 `ceil(N/10)`+1로 줄어드는 단계가 그대로 재현 |
| — | Criteria `LIKE … ESCAPE`가 `%`·`_`를 이스케이프한다 (003) | **확인됨** | `cb.like(path, pattern, '\\')` → `like ? escape '\\'`. 키워드 `%`는 "100% fun"만, `_`는 "a_b"만 일치(앞 문자를 `\`로 이스케이프해 넘긴 경우). **직접 이스케이프하지 않으면 보호되지 않음**은 별도 확인 안 함 |
| — | 낙관적 `@Version`과 직접 `UPDATE`가 같은 버전 규칙으로 맞물린다 (004) | **확인됨** | 직접 `UPDATE`가 `version=version+1`을 하면 낡은 엔티티 저장이 `ObjectOptimisticLockingFailureException`. **버전을 올리지 않는 직접 `UPDATE`는 충돌을 못 잡고 덮어씀(갱신 손실)**. 영향: 004의 직접 `UPDATE` 쿼리는 항상 `version = version + 1`을 포함해야 함 |
| — | 같은 컬럼의 읽기 전용 `@ManyToOne(insertable=false, updatable=false)`를 `Long` id와 병행해도 쓰기에 문제 없다 (003) | **확인됨** | `memberId`로 저장 후 트랜잭션 안에서 `member.getName()` 조회 성공, 수정 저장도 정상. 트랜잭션 밖에서 지연 로딩 접근은 `LazyInitializationException`(open-in-view 끔) — 서비스 계층 밖으로 엔티티를 내보내면 안 됨. 프록시의 public 필드는 `null`로 보이므로 접근자로 읽어야 함(스파이크에서 겪음) |

### 이 검증이 가리키는 문서 영향 (문서는 고치지 않음)

1. **005 (가장 중요)**: 위 5' — 스케줄러 트랜잭션의 격리 수준 또는 처리 단위 결정이 필요하다. `specs/005-close-groupbuy` research의 SKIP LOCKED 서술과 `tasks.md`의 동시 워커 테스트 태스크가 영향을 받는다.
2. **004**: 직접 `UPDATE`에는 `version = version + 1`을 포함하는 규칙을 명시하는 것이 좋다.
3. **환경**: Boot 4.x는 Hibernate 7을 쓴다. 문서에 적힌 API 이름이 Boot 3.x 기준이면 구현 때 맞춰야 한다(이번에 쓴 것은 모두 동작).
4. 이번에 **확인 못 한 것**: Java 21에서의 동작, 재기동 시 `schema.sql` 재실행, 실제 규모의 락 경합 빈도, 이스케이프 미적용 시 동작.
