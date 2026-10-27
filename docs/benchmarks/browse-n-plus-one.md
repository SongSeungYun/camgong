# 조회 N+1 개선 기록 (003, 헌법 II)

쿼리 수는 `QueryCountIT`가 `QueryCountInspector`(Hibernate `StatementInspector`)로 센다. 인증 필터의 회원 조회 1건은 상수라 제외하고, 조회 서비스가 쓰는 쿼리만 센다.
환경: MySQL 8.4 (Testcontainers), Spring Boot 4.1.2, Hibernate 7, Java 21.

## 시나리오 B: 상세의 개설자용 명단

| 단계 | 커밋 | N=1 | N=20 | N=50 | 비고 |
|------|------|-----|------|------|------|
| ① 순진한 구현 | `e38d78f` | 4 | 23 | 53 | 공구+개설자(join fetch) 1, 내 참여 1, 인원 수 1, 명단 1, 이후 참여자마다 `participation.getMember()` 지연 로딩. N=1은 개설자가 이미 영속성 컨텍스트에 있어 추가 쿼리가 없다. 즉 `N + 3` |
| ② fetch join | `2a35d39` | 4 | 4 | 4 | 명단을 `join fetch p.member`로 한 번에 읽는다. 참여자 수와 무관하게 4쿼리 |
| ③ batch size | `5ad73aa` | 4 | 5 | 5 | 쿼리를 순진한 구현으로 되돌리고 `hibernate.default_batch_fetch_size=100`만 켰다. 지연 로딩 N번이 `IN (…)` 한 번으로 묶인다. N=1은 개설자가 컨텍스트에 있어 추가 쿼리가 없다. 한 번에 최대 100개까지라 N>100이면 `ceil(N/100)`번 |
| ④ DTO 프로젝션 | | | | | |
| 최종 상한 | | 4 이하 | 4 이하 | 4 이하 | T030 |

## 시나리오 A: 내가 참여한 공구 한 페이지

| 단계 | 커밋 | N=1 | N=20 | N=50 | 비고 |
|------|------|-----|------|------|------|
| ① 순진한 구현 | `23e6e5f` | 2 | 21 | 51 | 참여 행 한 페이지 1, 이후 항목마다 `participation.getGroupBuy()` 지연 로딩. 즉 `1 + N` |
| ② fetch join | `2a35d39` | 1 | 1 | 1 | `join fetch p.groupBuy`로 참여 행과 공구를 한 쿼리로 읽는다. 항목 수와 무관하게 1쿼리. to-one 조인이라 행 중복이 없고 `LIMIT`을 그대로 쓸 수 있다 |
| ③ batch size | `5ad73aa` | 2 | 2 | 2 | 같은 방식. 참여 행 1 + 공구 `IN` 1. fetch join보다 1쿼리 많지만 쿼리 코드를 바꾸지 않는다 |
| ④ DTO 프로젝션 | | | | | |
| 최종 상한 | | 2 이하 | 2 이하 | 2 이하 | T030 |
