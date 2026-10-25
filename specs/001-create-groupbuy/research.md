# Research: 공구 개설

모든 항목은 결정 완료 상태이며 NEEDS CLARIFICATION은 없다. 항목 1~4는 사용자가 직접 고른 결정이고,
항목 5 이후는 기본값이다(바꾸고 싶으면 알려줄 것). 헌법 III에 따라 각 결정은 구현 전에 사용자가 자기 말로 설명할 수 있어야 한다.

## 사용자 결정

### 1. 공구 취소는 상태만 바꾼다

- **Decision**: `status`를 `CANCELLED`로 바꾼다. 조회(GET)와 참여에서는 없는 것처럼 취급한다. 행은 지우지 않는다.
- **Rationale**: 취소 알림은 참여자 목록이 필요하다. 지우기 전에 알림 대상 조회를 끝내야 하는 순서 의존이 사라지고, 이력(누가 언제 취소했나)도 남는다.
- **Alternatives**: 물리 삭제. 테이블은 깔끔하지만 알림 대상 조회·이력이 약하고, 취소와 동시에 들어온 참여가 사라진 행을 참조하는 경쟁이 생긴다.
- **설명 체크**: "없어진다"는 요구를 왜 삭제가 아니라 상태로 풀었는가?

### 2. 확보 수량은 공구 행의 컬럼으로 관리한다

- **Decision**: `group_buy.secured_quantity`를 두고 참여 수량이 바뀔 때 같은 트랜잭션에서 갱신한다. DB `CHECK (secured_quantity BETWEEN 1 AND total_quantity)`로 초과를 최후 방어한다.
- **Rationale**: 모집 완료 판정(secured = total)이 한 행 비교로 끝난다. 마지막 남은 수량 경쟁(3단계 4방식 비교: 락 없음/비관적/낙관적/조건부 UPDATE)이 모두 이 컬럼 위에서 성립한다.
- **Alternatives**: 매번 `SUM(participation.quantity)`. 값이 어긋날 일은 없지만 합산과 삽입 사이 경쟁을 락으로 막아야 하고, 목록 조회에서 집계 쿼리나 N+1이 생긴다.
- **설명 체크**: 컬럼과 참여 테이블의 합이 어긋나지 않게 하려면 어디서 무엇을 지켜야 하는가?

### 3. 10초 중복 개설은 만료 시각 가드 테이블로 막는다

- **Decision**: `create_guard(member_id, fingerprint, created_at)`, PK는 `(member_id, fingerprint)`. 개설 트랜잭션 안에서 아래를 실행하고 영향 행 수로 판정한다.

  ```sql
  INSERT INTO create_guard (member_id, fingerprint, created_at) VALUES (:m, :f, :now) AS new
  ON DUPLICATE KEY UPDATE created_at = IF(created_at < :now - INTERVAL 10 SECOND, new.created_at, created_at)
  ```

  영향 행 1 = 신규, 2 = 10초 지난 가드를 갱신 → 개설 진행. 0 = 10초 안 중복 → 409 `DUPLICATE_REQUEST`.
  이 판정은 JDBC URL의 `useAffectedRows=true`를 전제한다. 이 옵션이 없으면 Connector/J가 변경 없는 중복도 1로 돌려줘서 신규 삽입과 구분되지 않는다.
  `fingerprint`는 정규화한 입력값 전체의 SHA-256(Java `MessageDigest`)이다. 시각은 DB의 `NOW()`가 아니라 애플리케이션 `Clock`에서 파라미터로 넘긴다(테스트에서 시간을 제어하기 위해).
- **Rationale**: 동시 요청이 PK 충돌 행에서 InnoDB 락으로 직렬화되어 둘 중 하나만 통과한다. 첫 요청이 롤백(검증 실패 등)하면 가드도 같이 롤백되어 두 번째가 통과한다. 슬라이딩 10초라 FR-009를 정확히 지킨다.
- **Alternatives**: (a) 조회 후 삽입 — 동시에 오면 둘 다 통과한다. (b) 10초 구간 번호 unique — 구간 경계에서 10초 안에 두 개가 생길 수 있어 FR-009를 못 지킨다.
- **알려진 한계 (ponytail)**: MySQL 전용 문법이다(행 별칭은 8.0.19 이상). 가드 행은 쌓이므로 정리 작업이 필요해지면 만료 행 삭제를 추가한다.
- **설명 체크**: 영향 행 수가 0/1/2일 때 각각 무슨 일이 일어났는가? 구간 번호 방식은 왜 안 되는가?

### 4. 수정·취소는 공구 행 비관적 락으로 직렬화한다

- **Decision**: 수정·취소는 `SELECT ... FOR UPDATE`(JPA `PESSIMISTIC_WRITE`)로 공구 행을 잡고 검증 → 변경한다. 락 안에서 상태·마감·수량을 모두 최신 값으로 다시 확인한다.
- **Rationale**: 개설자 한 명이 드물게 호출하는 작업이고 여러 필드를 서로 의존해서 검증한다(수량 합, 마감 연장, 상태). 락으로 직렬화하면 재시도·충돌 처리 코드가 필요 없다. 취소와 수정이 겹쳐도 한쪽이 끝난 뒤 최신 상태를 본다.
- **Alternatives**: 낙관적 락(`@Version`, 충돌 시 409) — 대기는 없지만 재시도·실패 응답 코드가 늘어난다. 조건부 UPDATE — 수량 변경엔 좋지만 수정 필드가 많고 조건이 복잡해진다.
- **범위 경계**: 참여 경쟁(마지막 수량)에 어떤 방식을 쓸지는 3단계 비교 대상이다. 참여가 다른 방식을 쓰더라도 같은 행의 `version` 컬럼은 JPA가 갱신하므로 수정·취소의 락과 함께 동작한다(참여 기능 설계 때 재확인).
- **설명 체크**: 수정과 취소가 동시에 오면 어떤 순서로 무슨 결과가 나오는가?

## 기본값 결정

### 5. 알림은 Spring 이벤트로 발행만 한다

- **Decision**: 수정·취소 트랜잭션에서 `GroupBuyChanged`/`GroupBuyCancelled` 이벤트(공구 id, 알림 대상 회원 id 목록=개설자 제외 참여자, 변경 필드)를 발행하고 `@TransactionalEventListener(AFTER_COMMIT)`에서 로그만 남긴다. 실제 전달 수단은 알림 기능에서 리스너를 붙인다.
- **Rationale**: 커밋 전에 알림이 나가 롤백되는 문제를 피한다. 구현체가 하나뿐인 포트 인터페이스를 만들지 않는다.
- **Alternatives**: 서비스에서 직접 알림 호출(롤백 시 알림이 이미 나감), 아웃박스 테이블(재전송 보장이 필요해질 때).
- **알려진 한계 (ponytail)**: 커밋 후 리스너가 실패하거나 프로세스가 죽으면 알림이 유실된다. 신뢰성이 필요해지면 아웃박스로 올린다.
- **규칙**: 오픈채팅 링크만 바뀐 수정은 이벤트를 발행하지 않는다(FR-017).

### 6. 수정은 `PUT`(전체 교체), 취소는 `POST .../cancel`

- **Decision**: 수정 가능한 필드를 모두 담은 본문으로 `PUT /api/groupbuys/{id}`. 취소는 `POST /api/groupbuys/{id}/cancel`.
- **Rationale**: `PATCH`는 "생략"과 "null(링크 지우기)"을 구분해야 한다. `PUT`이면 구분이 필요 없고 개설과 같은 요청 record를 쓴다. 취소는 삭제가 아니라 상태 전환이라 `DELETE`를 쓰지 않는다(결정 1).
- **Alternatives**: JSON Merge Patch.

### 7. 오류는 ProblemDetail(RFC 7807)

- **Decision**: Spring 내장 `ProblemDetail`에 `errors: [{field, message}]` 속성을 더한다. 상태 코드: 400 입력 오류(FR-003~007, 012, 013, 017), 401 `X-Member-Id` 없음(FR-001), 403 개설자 아님(FR-011), 404 없음 또는 취소됨(GET), 409 상태 충돌·중복 요청.
- **Rationale**: FR-010 "무엇이 왜 잘못됐는지"를 필드 단위로 줄 수 있고 추가 의존성이 없다.

### 8. 검증은 단일 필드는 Bean Validation, 그 외는 서비스

- **Decision**: 필수·길이·양수·URL 형식은 `@NotBlank/@Size/@Min/@Max/@Pattern`. 시계가 필요하거나 다른 값·현재 상태에 의존하는 규칙(마감 미래/30일/연장만, 개설자 수량 < 총 수량, 수량 합)은 서비스에서 `Clock`으로 검사하고 같은 `errors` 형식으로 던진다.
- **Rationale**: `@Future`는 시스템 시계를 쓰므로 테스트에서 시간을 제어할 수 없다.

### 9. 시간은 UTC 저장, API는 오프셋 포함 ISO-8601

- **Decision**: 엔티티는 `Instant`, API는 `OffsetDateTime`. `hibernate.jdbc.time_zone=UTC`. 시간 의존 로직은 주입한 `java.time.Clock`을 쓴다.

### 10. 스키마 관리는 `schema.sql` + `ddl-auto=validate`

- **Decision**: Flyway 없이 Spring 내장 SQL 초기화를 쓴다.
- **Rationale**: 새 의존성이 없고 헌법의 "스키마 변경 추적" 요건은 저장소의 `schema.sql` 이력으로 충족한다.
- **알려진 한계 (ponytail)**: 운영 데이터가 생기는 5단계 전에 Flyway로 올려야 한다.

### 11. 빌드와 의존성

- **Decision**: Gradle. 의존성은 `spring-boot-starter-web/data-jpa/validation`, MySQL 드라이버, 테스트용 `spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers` mysql·junit-jupiter. Spring Boot와 Testcontainers 버전은 start.spring.io 생성 시점의 기본값을 따르고 이 문서에 고정하지 않는다.
- **Rationale**: 헌법 기술 제약(Java 21, Spring Boot, MySQL, Testcontainers) 그대로다.
