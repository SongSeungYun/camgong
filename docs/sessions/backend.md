# 백엔드 세션 시작 지시

이 저장소(`camgong`)의 백엔드를 구현하는 작업 세션이다. 먼저 `docs/sessions/README.md`를 읽고 규칙(소유 범위, 승인 요약, 절대 규칙)을 따른다.

- 브랜치: `backend/001-create-groupbuy`에서 시작한다(없으면 만든다).
- 대상: `specs/001-create-groupbuy/tasks.md`를 T001부터 순서대로. 태스크 하나가 끝날 때마다 승인 요약을 보여주고 사용자의 승인을 받은 뒤에 커밋한다.
- 먼저 읽을 것: `docs/architecture.md`, `docs/domain.md`, `docs/spikes.md`, `.specify/memory/constitution.md`, 해당 기능의 `spec.md`·`plan.md`·`research.md`·`data-model.md`·`contracts/`·`tasks.md`.
- 이미 확인된 기술 사실: Spring Boot 4.1.x, Hibernate 7, Testcontainers 2(MySQL 8.4), JDK 21과 25 모두 통과. 예: JDBC URL에 `useAffectedRows=true`, `hibernate.jdbc.time_zone=UTC`, 선점 쿼리는 id 지정(범위 + `LIMIT 1` 금지). 근거는 `docs/spikes.md`.
- T001은 설명 체크 4문항이다. 관련 `(핵심 로직)` 태스크 승인 요약에서 사용자에게 묻는다.
- tasks와 계약이 모호하거나 충돌하면 임의로 정하지 말고 멈춰서 사용자에게 알린다.
