# 1GB 서버 메모리 측정 (013, 기록용)

`specs/013-one-gb-server` plan 결정 5. "1GB 서버에서 OOM 없이 돈다"를 로컬에서 확인한 기록이다. 메모리를 보려는 측정이고 속도 측정은 아니다. 머지: 백엔드 PR #37(`9f25801`, head `517dc8f`).

## 환경

맥(Docker Desktop VM 10코어·16GB)에서 운영 `deploy/compose.yaml`을 띄웠다. 프로젝트 이름은 `camgong-onegb`이고 볼륨도 따로 썼다. `DOMAIN=localhost`라 Caddy 내부 인증서를 쓴다.
- k6는 맥에서 `https://localhost`(Caddy 경유)로 보냈다. 기존 시나리오 7개를 차례로 돌렸다.
  - browse: 공구 1만·학교 20, 20VU
  - mixed: 60초
  - last-seat
  - s2
  - check-sheet: 60초
  - list-during-close: 공구 2,000건이 60초 안에 마감
  - notifications: 참여 1,000명 공구 취소 → 알림 1,000건, 60초
- `docker stats`, 컨테이너별 cgroup(`memory.peak`·`memory.swap.peak`·`memory.events`), VM 스왑을 2~5초마다 기록했다.
- 이미지는 맥의 arm64다. 서버는 amd64다.
- web은 정적 파일 없는 `caddy:2`로 바꿔 돌렸다. Caddyfile과 `/api` 프록시는 같다. 처리방침 자리표시 4개가 남아 있어서 `build:release`가 일부러 멈추기 때문이다. 운영 web은 dist를 서빙하므로 페이지 캐시가 더 붙는다.

## 설정 (머지된 값)

| 서비스 | `mem_limit` | 설정 |
|--------|-------------|------|
| mysql | 288m | `deploy/mysql/camgong.cnf`: `performance_schema=OFF`, `mysqlx=OFF`, 버퍼 풀 128M(기본값과 같음), 로그 버퍼 8M, `max_connections=40`, tmp/heap 테이블 16M, `temptable_max_ram=64M`(8.4 기본 1GiB), table open/definition 캐시 400 |
| backend | 460m | `-Xms224m -Xmx224m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss512k -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`, `MALLOC_ARENA_MAX=2`. Tomcat·Hikari는 기본값 |
| web | 128m | `GOMEMLIMIT=64MiB` |

한도 합은 876m다. 서버에는 스왑 2GB를 붙인다(plan 결정 3). 컨테이너는 따로 정하지 않았으므로 각자 `mem_limit`만큼 스왑을 쓸 수 있다.

## 결과

| 항목 | 기준선(한도 없음) | 한도(final3) | 한도(final2) |
|------|------------------|--------------|--------------|
| mysql docker stats 최대 / memory.peak | 529.9MiB / — | 219.5 / 283.5MiB | 224.5 / 258.7MiB |
| backend docker stats 최대 / memory.peak | 803.9MiB / — | 411.5 / **460.0MiB(한도)** | 398.7 / 460.0MiB |
| web docker stats 최대 / memory.peak | 95.5MiB / — | 87.6 / 109.0MiB | 78.0 / 112.1MiB |
| 컨테이너 스왑 최대 | 0 | backend 0.2MiB, 나머지 0 | backend 4.0MiB |
| VM 스왑 최대 → 끝 | 0 | 0.1 → 0.0MiB | 2.2 → 1.3MiB |
| OOMKilled·재시작·`OutOfMemoryError` | 0 | 0 | 0 |
| 5xx / 의도하지 않은 4xx / check 실패 | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |

- docker stats는 inactive_file을 빼고 센다. `memory.peak`는 페이지 캐시까지 센다.
- 4xx는 의도한 409(NOT_JOINABLE·INSUFFICIENT)만 나왔다. last-seat에서 99건이고, s2에서 기준선 47건, final3 47건, final2 46건이다.
- backend는 페이지 캐시까지 합쳐 한도에 닿는다. 그때 캐시를 비우며 돌았다: `memory.events max=66`(final2 81), `oom_kill 0`. 측정이 끝난 시점에 익명 메모리는 416MB(final2 402MB), 파일 캐시는 33MB였다.
- final2는 맥 load average가 약 6일 때 돌아 처리량이 떨어졌다(mixed 요청 34k). 그래서 새 스택으로 final3를 다시 돌렸다. 메모리 수치는 두 번이 비슷하다.

p95(ms). 기록만 한다(목표 수치를 정한 적이 없다).

| 시나리오 | 기준선 | final3 | final2 |
|----------|--------|--------|--------|
| browse | 21.6 | 11.5 | 13.7 |
| mixed | 24.6 | 16.6 | 59.7 |
| last-seat | 55.8 | 207.5 | 57.3 |
| s2 | 25.6 | 17.0 | 216.5 |
| check-sheet | 3.5 | 2.6 | 9.0 |
| list-during-close | 3.6 | 2.7 | 7.5 |
| notifications | 2.4 | 1.8 | 4.6 |

- final3 요청 수: mixed 130,168, check-sheet 197,936, notifications 291,504.
- last-seat·s2는 100명·50명이 동시에 한 번씩 보내는 시나리오다. 그래서 한 번 튀면 p95가 흔들린다. 튄 원인은 확인 못 했다. 두 번 다 스왑이 0 가까울 때 났다. SerialGC 멈춤일 수도 있지만 GC 로그는 보지 않았다.

**판정**: plan 결정 5의 통과 조건을 로컬에서 충족했다(OOMKilled 0, `OutOfMemoryError` 0, 의도한 409 말고는 오류 0, 스왑이 계속 늘지 않음).

## 한도를 정한 과정

- 첫 시도(mysql 320m, backend 420m, 아레나 기본값)는 통과하지 못했다. backend가 420MiB 한도에 붙은 채 스왑을 118MiB 썼다(mixed 동안 늘다가 그 뒤 평평). 처리량도 mixed 81k → 31k 요청으로 떨어졌다.
- NMT로 본 JVM 커밋은 약 428MiB였다: 힙 224, 메타스페이스 97, 심볼 39, 코드 35, 클래스 18, 공유 클래스 12.
- 힙은 줄이지 않았다(plan 결정 6). 대신 `MALLOC_ARENA_MAX=2`를 넣고 한도를 실측에 맞췄다.
- 검수에서 web을 96m → 128m로 올렸다. 96m는 한도 없을 때의 최대(95.5)와 같아서 한도까지 찼고 스왑을 9.2MiB 썼기 때문이다. `temptable_max_ram`도 같이 넣었다.
- 맥 CPU는 서버보다 빠르다. `cpus: 1`로 돌린 진단에서 browse p95는 197ms였다(CPU 탓).

## 한계와 남은 확인

- 실행마다 새 JVM으로 같은 시나리오를 돌렸는데, backend 익명 메모리가 384 → 402 → 416MB로 늘었다. 힙은 고정이고 메타스페이스(128m)와 코드 캐시(48m)에는 상한이 있다. 그래서 끝없이 늘지는 않는다고 보지만, 몇 시간짜리 측정은 하지 않았다.
- mysql `memory.peak`는 한도의 98%다. 파일 캐시를 포함한 값이고 스왑은 0이다. 버퍼 풀이 128M로 고정이라 데이터가 늘면 메모리보다 디스크 읽기가 먼저 는다.
- 남은 몫이 OS·dockerd에 필요한 메모리보다 작을 수 있다. 1GB 서버에서 쓸 수 있는 메모리는 약 960MiB(백엔드 추정, 서버의 `free`로 확인 못 함)이고, 여기서 한도 합 876m를 뺀 만큼이 남는다. 순간 초과는 스왑 2GB가 받는다는 전제다.
- 서버에서 할 확인은 `docs/deploy-checklist.md` 7.1에 있다(배포 직후, 며칠 뒤, 백업 중). 서버에서 스왑이 계속 늘면 2GB(월 $10) 전환을 사용자에게 묻는다(plan 결정 6).
- 실제 서버(1 vCPU)에서의 k6 재측정(plan 결정 5)은 아직 하지 않았다. 시드를 운영 DB에 넣을 수 없다. 방법은 서버가 준비되면 정한다.
