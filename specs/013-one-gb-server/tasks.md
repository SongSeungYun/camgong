# Tasks: 1GB 서버에 올리기

**Input**: [plan.md](plan.md)

규칙: docs/·specs/는 고치지 않는다(측정 결과는 보고만, 관제탑이 적는다). 테스트 컨테이너 설정은 건드리지 않는다. 012 머지 뒤 새 main에서 `backend/013-one-gb-server`로 시작한다.

- [x] T001 한도 없이 운영 compose를 로컬에서 띄워 기존 k6 시나리오를 돌리고 컨테이너별 최대 메모리·p95·오류율을 기록한다(기준선)
- [x] T002 `deploy/mysql/camgong.cnf`(plan 결정 1), 백엔드 `JAVA_TOOL_OPTIONS`(결정 2), 서비스별 `mem_limit`(결정 3), 각 `build:`에 `image:` 이름(결정 4). 운영에서 `performance_schema`를 앱이 쓰지 않는지 확인
- [x] T003 같은 시나리오를 한도를 건 채 돌려 plan 결정 5의 통과 조건을 확인하고, 기준선과 나란히 보고한다. 통과하지 못하면 결정 6
- [x] T004 맥에서 `linux/amd64` 이미지를 빌드해 `docker save`로 묶는 명령을 실제로 한 번 해 보고(크기 포함), 서버에서 할 `docker load` → `docker compose up -d --no-build` 명령과 함께 그대로 보고한다. 서버 접속은 사용자만 한다(관제탑이 체크리스트에 옮긴다)

## 의존 관계

012 머지 → T001 → T002 → T003 → T004.
