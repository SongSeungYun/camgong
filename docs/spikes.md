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
| 11 | `WHERE` 조건(초과, 종료 상태)이 영향 행 0으로 드러나고, 변화량 0도 `version` 증가로 영향 행 1이다 | +16(초과), 종료 상태에서 +1, +0 | 초과·`COMPLETED`는 0행, 변화량 0은 `version`이 올라 1행(`useAffectedRows=true`) | 004의 거부 판정과 no-op 변경 처리가 성립 |

## 미검증 (Spring 수준, 구현 시작 직전에 확인)

| # | 가정 | 확인 방법 |
|---|------|-----------|
| 6 | `schema.sql`이 `ddl-auto=validate`보다 먼저 실행된다 | Spring Boot 최소 프로젝트로 기동 |
| 7 | `@DynamicPropertySource`로 준 JDBC URL 옵션(`useAffectedRows`)이 실제로 적용된다 | 테스트에서 가드 영향 행 수 단언 (T012) |
| 8 | JPA `PESSIMISTIC_WRITE`가 `FOR UPDATE`로 나가고 `@Version`과 같이 동작한다 | SQL 로그 확인 |
| 9 | `Instant` ↔ `DATETIME(6)`가 UTC로 저장되고 `MutableClock` 기준 비교가 맞다 | 경계 테스트 (T020) |
