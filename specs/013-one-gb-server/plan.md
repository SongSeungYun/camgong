# Plan: 1GB 서버에 올리기

**Date**: 2026-11-25 | **배경**: 서버는 Vultr 서울 1 vCPU·1GB·25GB SSD(월 $5, 2026-11-24 사용자 결정, `docs/deploy-checklist.md` 7). 모자라면 같은 서버를 2GB(월 $10)로 올린다(올리기만 된다).

## Summary

`deploy/compose.yaml`의 세 컨테이너(MySQL 8.4, 백엔드, Caddy)가 메모리 1GB와 스왑 2GB 안에서 OOM 없이 돌게 메모리 한도를 정하고, 같은 한도로 로컬에서 부하를 걸어 확인한다. 이미지는 서버에서 빌드하지 않는다.

## Constitution Check

- I. 범위 상한: 배포 설정만 바꾼다. 앱 동작·스키마 변경 없음. 통과.
- II. 측정: "1GB에서 돈다"는 주장은 아래 측정으로만 한다. 측정 전에는 열지 않는다.
- III. 설명 가능: 결정마다 이유를 적는다.
- IV. 테스트: 앱 코드 변경이 없으면 새 테스트 의무 없음. 테스트 컨테이너 설정은 건드리지 않는다(011·012 테스트가 `performance_schema`를 쓴다).

## 결정

### 1. MySQL
- 설정 파일 `deploy/mysql/camgong.cnf`를 `/etc/mysql/conf.d/`에 읽기 전용으로 붙인다(긴 `command` 인자보다 검수하기 쉽다). 기존 `--disable-log-bin`은 유지한다.
- 후보 값(측정으로 정한다): `performance_schema=OFF`, `innodb_buffer_pool_size=128M`(지금 데이터가 작다), `innodb_log_buffer_size=8M`, `max_connections`는 백엔드 Hikari 풀 + 여유(수십), `tmp_table_size`·`max_heap_table_size` 16M, `table_open_cache` 축소. 기본 설정의 로컬 컨테이너가 531MB를 썼다(2026-11-23).
- 운영에서 `performance_schema`를 꺼도 앱은 쓰지 않는다(테스트 도우미만 쓴다). 확인한다.

### 2. 백엔드 JVM
- `JAVA_TOOL_OPTIONS`로 힙·메타스페이스·스레드 스택·코드 캐시 상한을 준다(예: `-Xmx256m`, `-XX:MaxMetaspaceSize`, `-Xss512k`, `-XX:+UseSerialGC`). 값은 측정으로 정한다. 힙이 모자라 `OutOfMemoryError`가 나면 실패다.
- Tomcat 스레드·Hikari 풀이 기본값으로 충분한지 같은 측정에서 본다(바꾸면 이유를 적는다).

### 3. 컨테이너 한도와 스왑
- compose에 서비스별 `mem_limit`을 준다(합이 1GB 안쪽, OS 몫을 남긴다). 한도를 넘으면 그 컨테이너만 OOM으로 죽고 `restart: unless-stopped`가 다시 띄운다. 그래서 측정에서 OOMKilled가 한 번도 없어야 한다.
- 서버에는 스왑 파일 2GB를 붙인다(`vm.swappiness` 낮게). 스왑은 순간적인 초과를 받아 주는 완충이지 평소 쓰는 메모리가 아니다: 측정 중 스왑 사용이 계속 늘면 실패로 본다. 설정 절차는 관제탑이 `docs/deploy-checklist.md`에 적는다.

### 4. 이미지는 서버 밖에서 빌드
- 1GB 서버에서 Gradle·Vite 빌드는 메모리가 모자라거나 매우 느리다. 맥(Apple Silicon)에서 `linux/amd64`로 빌드해 `docker save | ssh … docker load`로 옮기고, 서버는 `--no-build`로 띄운다. 레지스트리는 쓰지 않는다(계정·비밀값이 하나 줄어든다).
- compose의 각 `build:`에 `image:` 이름을 붙여, 로컬 빌드와 서버 실행이 같은 이름을 쓰게 한다.

### 5. 측정
- 로컬(Docker Desktop)에서 운영 compose와 같은 한도로 띄우고, 기존 k6 시나리오(`load/k6`)를 그대로 돌린다. 시드는 기존 측정 시드.
- 기록: 컨테이너별 최대 메모리(`docker stats`), OOMKilled 여부(`docker inspect`), 스왑 사용, 시나리오별 p95·오류율. 한도 없는 같은 실행과 나란히 적는다.
- 통과 조건: OOMKilled 0, `OutOfMemoryError` 0, 의도한 409·429 말고는 오류 0, 측정 중 스왑 사용이 계속 늘지 않음. p95는 기록만 한다(목표 수치를 정한 적이 없다).
- 로컬 맥은 CPU가 서버보다 빠르다. 메모리 확인용이지 속도 확인용이 아니다. 실제 서버에서 같은 시나리오를 한 번 더 짧게 돌려 확인한다(배포 뒤).
- 결과는 세션이 보고하고 관제탑이 `docs/benchmarks/one-gb-server.md`에 적는다.

### 6. 안 되면
- 측정이 통과하지 못하면 값을 줄이는 대신 사용자에게 2GB(월 $10) 전환을 묻는다. 버퍼 풀을 너무 줄이면 데이터가 늘 때 느려진다.
