# Research: 마감 확정(모집 실패 처리)

NEEDS CLARIFICATION은 없다. 결정 1~3은 사용자가 직접 고른 것이고, 4 이후는 기본값이다(바꾸고 싶으면 알려줄 것).
헌법 III에 따라 각 결정은 구현 전에 사용자가 설명할 수 있어야 한다.

## 사용자 결정

### 1. 중복 방지는 행 락 + `SKIP LOCKED`

- **Decision**: 공구 1건씩 아래 문장으로 선점한다. 행이 안 잡히면(다른 곳이 잡았거나, 이미 확정됐거나, 조건이 안 맞음) 그 공구는 건너뛴다.

  ```sql
  SELECT * FROM group_buy
  WHERE id = :id AND status = 'OPEN'
    AND deadline <= :cutoff            -- cutoff = now − 1초 (결정 5)
    AND secured_quantity < total_quantity
  FOR UPDATE SKIP LOCKED
  ```

  선점한 뒤 같은 트랜잭션에서 `status = 'FAILED'`로 바꾸고, 참여자 id를 모아 이벤트를 발행하고 커밋한다.
- **Rationale**: 락을 잡은 뒤 상태·마감을 다시 확인하므로 이미 확정된 공구는 자연히 제외되고(멱등), 인스턴스들이 서로 기다리지 않고 일을 나눈다. 001 수정·취소, 004 참여가 같은 공구 행 락을 쓰는 원리와 같아서 직렬화(FR-009)도 같은 행에서 일어난다. 스파이크로 동작을 확인했다([docs/spikes.md](../../docs/spikes.md) #12).
- **Alternatives**: (a) 건별 조건부 `UPDATE ... WHERE status='OPEN'`으로 선점하고 영향 행 1인 쪽만 이벤트 발행 — 대기는 없지만 참여자 조회를 선점 뒤에 따로 해야 한다. (b) `GET_LOCK('close-groupbuy', 0)`으로 한 대만 실행 — 구현은 쉽지만 세션 락이라 커넥션 풀에서 해제를 챙겨야 하고 실행자 한 대가 느리면 전체가 느려진다.
- **설명 체크**: 두 인스턴스가 같은 공구를 동시에 잡으려 하면 각각 무슨 일이 일어나고, 이미 확정된 공구를 늦게 온 인스턴스가 다시 처리하지 않는 이유는 무엇인가?

### 2. 실행 주기 30초 (`fixedDelay`)

- **Decision**: `@Scheduled(fixedDelay = 30s)`. 앱 시작 직후 바로 한 번 실행한다.
- **Rationale**: 마감 후 지연의 최악은 `주기 30초 + 안전 여유 1초 + 처리 시간`이라 목표(1분 이내 95%)를 여유 있게 만족한다. `fixedDelay`는 이전 실행이 끝난 뒤 30초를 세므로 같은 인스턴스의 실행이 겹치지 않는다. 인스턴스 간 겹침은 결정 1이 막는다.
- **Alternatives**: 10초 — 확정이 더 빨라지지만 조회가 3배로 늘고 이득이 목표 대비 작다. 60초 — 조회는 가장 적지만 최악 지연이 약 61초로 95% 목표가 아슬아슬하다.
- **설명 체크**: `fixedRate` 대신 `fixedDelay`를 쓴 이유는?

### 3. 트랜잭션 단위는 공구 1건씩

- **Decision**: `GroupBuyCloser.closeOne(id)`가 `@Transactional`로 공구 1건의 선점·확정·이벤트 발행을 한 트랜잭션에서 한다. `GroupBuyCloseService`가 예외를 잡아 기록하고 다음 공구로 넘어간다.
- **Rationale**: FR-008(한 건의 실패가 다른 건을 막지 않음)이 자연스럽게 지켜지고, 락 보유 시간이 짧아 004 참여·001 수정과의 충돌이 적다. 이 규모에서 왕복 증가는 부담이 아니다.
- **Alternatives**: 100건 묶음 — 왕복은 적지만 한 건이 실패하면 100건이 같이 롤백되고 락을 오래 잡는다.
- **설명 체크**: `closeOne`이 서비스와 같은 클래스의 메서드였다면 건별 트랜잭션이 왜 시작되지 않는가?

## 기본값 결정

### 4. 후보 조회와 선점을 두 단계로 나눈다

- **Decision**: (1) 락 없는 후보 조회 `SELECT id FROM group_buy WHERE status='OPEN' AND deadline <= :cutoff AND secured_quantity < total_quantity ORDER BY deadline LIMIT 100` → (2) 후보마다 결정 1의 선점 쿼리 실행. 라운드를 반복하되 (a) 후보가 없으면 끝, (b) 한 라운드에서 확정한 공구가 0건이면 끝(후보가 전부 다른 인스턴스에 잡혀 있을 때 무한 반복 방지), (c) 한 실행의 최대 라운드는 50(5,000건)으로 제한한다. 남은 것은 다음 실행(30초 뒤)이 처리한다.
- **Rationale**: 마감 시각이 이른 순으로 처리(FR-006)하면서 한꺼번에 많은 락을 잡지 않는다. 백로그(재시작 직후)도 몇 라운드 안에 소화한다.
- **한계 (ponytail)**: 후보 조회와 선점 사이에 상태가 바뀔 수 있지만, 선점 쿼리가 조건을 다시 확인하므로 안전하다. 인덱스가 없으면 전체 스캔이 되므로 결정 8의 인덱스가 필요하다.

### 5. 안전 여유 1초 (`cutoff = now − 1초`)

- **Decision**: 선점 조건의 `:cutoff`는 `Instant.now(clock) − 1초`이다(004 결정 3). 마감 판정은 다른 요청과 같은 앱 `Clock`을 쓰되 확정만 1초 늦춰 인스턴스 간 시계 오차로 마감 전 공구가 실패로 확정되는 것을 막는다. 참여·수정·취소는 `deadline <= now`부터 즉시 거부되므로 마감~확정 사이 최대 약 31초는 '마감됨(확정 대기)'다.
- **Rationale**: 005 FR-010. 서버 시계가 동기화되어 있다는 가정 아래 오차의 상한을 1초로 본다.

### 6. 실패 이벤트

- **Decision**: 선점한 같은 트랜잭션에서 개설자를 포함한 모든 참여 행의 회원 id로 `GroupBuyFailed(groupBuyId, recipientIds)`를 발행하고 커밋 후(`AFTER_COMMIT`)에 소비한다. 롤백되면 이벤트도 나가지 않는다. 한계는 spec과 같다(커밋 직후 서버가 멈추면 유실될 수 있음).
- **Rationale**: 상태 확정과 이벤트가 같은 트랜잭션 경계 안에서만 나가므로 중복 발행이 없다(FR-007).

### 7. 관측: 실행마다 로그 한 줄

- **Decision**: 실행이 끝나면 `INFO close-run closed=12 skipped=3 failed=1 rounds=2 durationMs=420`를 남기고, 실패한 공구는 `WARN close-failed groupBuyId=… reason=…`로 남긴다(FR-012). 후보가 하나도 없으면 로그를 남기지 않는다(30초마다 쌓이는 소음 방지).

### 8. 인덱스

- **Decision**: `group_buy (status, deadline)` 인덱스를 추가한다(`schema.sql`, 001 파일 수정). 후보 조회가 `status='OPEN'`과 `deadline` 범위, `ORDER BY deadline`을 쓴다.
- **Rationale**: 공구가 쌓일수록 30초마다의 후보 조회가 전체 스캔이 되는 것을 막는다.

### 9. 설정

- **Decision**: `camgong.scheduler.close.enabled`(기본 `true`), `interval`(기본 `30s`), `margin`(기본 `1s`), `batch-size`(기본 `100`), `max-rounds`(기본 `50`). 테스트 프로파일은 `enabled=false`로 두고 서비스를 직접 호출한다.

### 10. 여러 인스턴스는 스레드로 모사한다

- **Decision**: `CloseConcurrencyIT`는 같은 DB에 대해 N개 스레드가 각자 `closeExpired()`를 동시에 호출한다. 각 스레드는 독립된 커넥션을 쓴다. 이것이 서버 N대와 DB 입장에서 같다.
- **Rationale**: Testcontainers 하나로 앱 여러 대를 띄우는 것보다 단순하고 빠르다. 락 경쟁은 DB 쪽에서 일어난다.

## 미검증 가정 (구현 시작 직전에 확인)

| # | 가정 | 확인 방법 |
|---|------|-----------|
| 1 | 네이티브 `SKIP LOCKED` 쿼리가 JPA에서 영속 엔티티로 돌아온다 | SQL 로그와 통합 테스트 |
| 2 | 별도 빈의 `@Transactional`이 건마다 새 트랜잭션을 연다 | 통합 테스트 |
| 3 | 후보 조회가 인덱스 `(status, deadline)`을 탄다 | `EXPLAIN` |
