# 밤샘 작업 큐

**시작**: 2026-10-26 | **브랜치**: `overnight/2026-10-26` (로컬 커밋만, push 금지) | **원칙**: 설계가 확정된 범위 안의 기계적·분석 작업만 한다.

루프는 깨어날 때마다 이 파일을 읽고 **미완료(`[ ]`) 작업 하나**만 수행한 뒤, 상태·로그를 이 파일에 기록하고 커밋하고 다음 깨어남을 예약한다. 큐가 끝나면 `docs/overnight/REPORT.md`를 쓰고 **더 이상 예약하지 않고 멈춘다.**

## 절대 규칙

1. **구현 코드를 작성하지 않는다.** 락·쿼리·상태 전이 같은 핵심 로직은 사용자가 직접 쓰기로 했다. J4의 스파이크 코드는 `scratchpad` 안에서만 만들고 저장소에 넣지 않는다.
2. **설계 결정을 확정하지 않는다.** 결정이 필요하면 후보 A/B/C, 추천, 근거를 적고 `상태: 사용자 결정 대기`로 표시한다. 사용자에게 질문하지 않는다(자는 중이다).
3. **기존 `spec.md`, 헌법, README는 수정하지 않는다**(권한으로도 막혀 있다). 기존 `plan/research/data-model/contracts/quickstart/tasks`도 고치지 않는다. 문제를 발견하면 보고서에 적는다. 새 문서(002·006·007의 plan 등)와 새 보고서만 만든다.
4. **`git push`, `gh`, 브랜치 삭제, `rm`, 설치를 하지 않는다.** 커밋은 내가 만든 경로만 `git add docs …` / `git add specs …`로 고르고 `git add -A`를 쓰지 않는다. 커밋 메시지는 `docs:` 접두사와 `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` 줄을 쓴다.
5. **모르면 지어내지 않는다.** 확인하지 못한 것은 "확인 못 함"이라고 적는다. 측정하지 않은 수치를 쓰지 않는다.
6. 한 작업이 **3번 실패**하거나 **약 90분**을 넘기면 로그에 사유를 적고 `[건너뜀]`으로 표시하고 다음 작업으로 간다. 권한 때문에 막혀도 같다.
7. 같은 일을 다시 하지 않는다. 시작 전에 이 파일의 상태와 `git log`를 확인한다.

## 작업 상태

- [x] **J1** 003·004·005 `tasks.md` 생성 + 기능별 analyze
- [x] **J2** graphify 그래프 + 2차 교차 점검
- [x] **J3** 002·006·007 plan 초안 (결정 대기)
- [x] **J4** Spring 수준 스파이크

## J1. tasks 생성과 analyze

대상: `003-browse-groupbuy`, `004-participate-groupbuy`, `005-close-groupbuy` (한 번에 한 기능씩 진행하고 기능마다 커밋).

1. `.specify/feature.json`을 대상 기능 디렉터리로 바꾼다(`{"feature_directory": "specs/00N-…"}`).
2. `/speckit-tasks`로 `tasks.md`를 만든다. 001의 `specs/001-create-groupbuy/tasks.md` 형식을 따른다: 체크박스, ID, `[P]`, `[US]`, 파일 경로, 제약은 문서에서 그대로 인용, **(핵심 로직)** 표시(락·쿼리·상태 전이는 사용자가 직접 쓴다), 테스트 먼저, 헌법 IV 필수 테스트 대응. 하위 패키지는 `groupbuy.query`(003), `groupbuy.join`(004), `groupbuy.close`(005)다.
3. `/speckit-analyze`를 돌려 결과(표, 커버리지, 헌법 정렬)를 `docs/overnight/analyze-00N.md`에 저장한다. **분석은 읽기 전용이다.** 발견한 문제는 고치지 않고 파일에 적는다.
4. 끝나면 `.specify/feature.json`을 `specs/007-notify`로 되돌린다.
5. 커밋: `docs: add 00N tasks and analysis (overnight)`.

완료 조건: 세 기능 모두 `tasks.md`와 `analyze-00N.md`가 있다.

## J2. graphify 그래프와 2차 교차 점검

1. 코퍼스를 스크래치패드에 복사한다(저장소를 오염시키지 않기 위해). 매번 새 폴더 `graph-corpus-<날짜시각>`을 쓰고 지우지 않는다. 복사 대상: `docs/`, `specs/`(체크리스트 포함), `README.md`, `.specify/memory/constitution.md`(→ `constitution.md`).
2. graphify 스킬(`/graphify`)의 파이프라인을 **그 폴더 안에서** 실행한다. 파이썬은 `/Users/song/.local/share/uv/tools/graphifyy/bin/python`이다(이미 설치됨, 설치하지 않는다). 의미 추출은 스킬 지침대로 `subagent_type="general-purpose"` Agent로 나눠서 한다. 예상 약 2개 청크.
3. 그래프가 만들어지면 `graphify query`로 다음을 묻고 근거(source_location)를 인용해 기록한다.
   - 어떤 요구사항(FR/SC)이 어떤 검증 시나리오(quickstart)에도 연결되지 않는가?
   - 서로 다른 기능 문서가 같은 개념을 다른 이름으로 부르는가?
   - 어느 커뮤니티에도 연결되지 않은 고립 개념은?
   - 기능 사이를 잇는 핵심 개념(god node)은 무엇이며 문서가 그 개념을 일관되게 쓰는가?
4. 그래프와 별개로 **결정적 점검 스크립트**를 `docs/overnight/checks.py`로 만들고 실행한다(표준 라이브러리만): (a) 각 `spec.md`의 FR-###, SC-###이 같은 기능의 `quickstart.md` 표 'spec' 열에 등장하는지, (b) 문서 안의 상대 링크가 깨졌는지, (c) 용어집이 금지한 말("정원", "신청", "주문" 등 `docs/domain.md` 용어집의 '쓰지 않는 말')이 본문에 쓰였는지, (d) 이벤트·상태 이름(`GroupBuyCompleted`, `PENDING_CLOSE` 등)이 문서마다 같은지.
5. 결과를 `docs/cross-check-2.md`에 정리한다: 발견 표(ID, 위치, 내용, 심각도, 제안), 그래프 요약(노드·엣지·커뮤니티 수, 토큰 사용량), 확인 못 한 것. **기존 문서는 고치지 않는다.**
6. `graphify-out/`은 스크래치패드에 남기고 저장소에는 넣지 않는다. 커밋: `docs: add 2nd cross-check with graph and scripts (overnight)`.

완료 조건: `docs/cross-check-2.md`와 `docs/overnight/checks.py`가 있다. graphify 단계가 실패하면 스크립트 점검(4)만으로 보고서를 쓰고 그래프 실패 사유를 적는다.

## J3. 002·006·007 plan 초안

대상 순서: `006-organizer-check`, `007-notify`, `002-login-signup` (결정이 적은 순).

1. 기능마다 `.specify/feature.json`을 바꾸고 `.specify/scripts/bash/setup-plan.sh --json`을 실행한 뒤, 003·004·005의 plan 산출물(`plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`) 구조와 문체를 따라 쓴다.
2. 각 `research.md`의 결정은 **전부 `상태: 사용자 결정 대기`**로 쓴다. 형식: 후보 A/B/C, 각각의 장단점, **추천과 이유**, `설명 체크` 문항. 사용자가 고를 수 있게 결정 지점을 모아 `docs/overnight/decisions-pending.md`에 목록으로도 정리한다.
3. 결정 지점 예시(이외에도 발견하면 추가): 
   - 002: 로그인 상태 유지 방식(세션/토큰), 비밀번호 저장 방식, 인증 메일 발송 수단과 개발·테스트 환경의 대체, 허용 도메인 설정 방식, 인증 링크 토큰 저장 방식, 로그인 실패 기록의 저장 위치.
   - 006: 체크 API 형태(PUT으로 목표 상태 지정), `participation` 컬럼 추가 방식, 체크표 쿼리와 N+1(003 명단과의 공유), 동시 체크 처리.
   - 007: 알림 테이블 설계, 이벤트 리스너에서 알림을 만드는 트랜잭션 경계(`AFTER_COMMIT` 후 새 트랜잭션), 대상 많은 사건의 생성 방식(배치), 읽지 않은 개수 조회, 30일 정리 작업(005 스케줄러 패턴 재사용 여부).
4. 003·004·005·001의 확정 사항(패키지 `groupbuy.*`, `Clock`, `ProblemDetail`, 이벤트, 오류 코드, `status` 필드명)과 충돌하지 않게 쓴다. 충돌이 보이면 보고서에 적는다.
5. 기능마다 커밋: `docs: add 00N plan draft pending decisions (overnight)`. 끝나면 `.specify/feature.json`을 `specs/007-notify`로 되돌린다.

완료 조건: 세 기능에 plan 산출물이 있고 `decisions-pending.md`가 있다.

## J4. Spring 수준 스파이크

목적: `docs/spikes.md`와 각 `research.md` 끝의 **미검증 가정**을 임시 프로젝트로 확인한다. 구현이 아니다. JDK는 설치된 25를 쓰고, 결과에 "JDK 25로 검증"을 명시한다(프로젝트 목표는 Java 21).

1. 스크래치패드에 `spring-spike-<날짜시각>/` Maven 프로젝트를 만든다. Spring Boot 버전은 `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml`에서 정식 릴리스 중 최신을 고르고, JDK 25에서 빌드가 안 되면 한 단계 낮은 안정 버전을 시도한다. 버전과 시도 기록을 남긴다. 의존성: web, data-jpa, validation, MySQL 드라이버, Testcontainers(MySQL 8.4, 컨테이너 이름은 `camgong-` 접두사).
2. 확인할 가정(출처 문서와 함께 결과를 기록): 
   - `schema.sql`이 `ddl-auto=validate`보다 먼저 실행된다 (spikes #6)
   - `@DynamicPropertySource`로 준 JDBC URL 옵션 `useAffectedRows=true`가 실제 적용되어 중복 가드 영향 행 수가 0/1/2로 구분된다 (#7)
   - JPA `PESSIMISTIC_WRITE`가 `FOR UPDATE`로 나가고 `@Version`과 함께 동작한다 (#8)
   - `Instant` ↔ `DATETIME(6)` UTC 저장과 `MutableClock` 비교 (#9)
   - 네이티브 `SELECT … FOR UPDATE SKIP LOCKED`가 영속 엔티티로 돌아온다 (005 research)
   - 다른 빈의 `@Transactional`이 건마다 새 트랜잭션을 연다 / 같은 클래스 호출은 안 연다 (005 research)
   - 후보 조회가 인덱스 `(status, deadline)`을 탄다 (`EXPLAIN`) (005 research)
   - Hibernate `StatementInspector`가 지연 로딩·fetch join 쿼리를 센다 (003 research)
   - Criteria API `LIKE … ESCAPE`가 MySQL에서 `%`·`_`를 이스케이프한다 (003 research)
   - `hibernate.default_batch_fetch_size`가 `IN` 쿼리로 나간다 (003 research)
   - 낙관적 `@Version` 갱신과 직접 `UPDATE`(엔티티 바이패스)가 같은 버전 규칙으로 맞물린다 (004 research)
   - 같은 컬럼에 읽기 전용 `@ManyToOne(insertable=false, updatable=false)`를 병행해도 쓰기에 문제가 없다 (003 research)
3. 각 가정마다 판정을 `확인됨 / 틀림 / 확인 못 함` 중 하나로 쓰고 증거(실행 결과 요약)를 붙인다. **틀린 가정은 어떤 문서의 어느 결정이 영향받는지** 적는다. 문서는 고치지 않는다.
4. `docs/spikes.md`에 새 절 "Spring 수준 검증 (JDK 25, Boot x.y.z)"를 **추가**한다(기존 내용은 건드리지 않는다). 스파이크 코드는 저장소에 넣지 않는다.
5. 컨테이너는 끝나면 `docker rm -f camgong-…`으로 정리한다. 커밋: `docs: record Spring-level spike results (overnight)`.

완료 조건: `docs/spikes.md`에 위 가정별 판정이 있다. 일부는 `확인 못 함`이어도 된다.

## 로그

(루프가 작업을 끝낼 때마다 한 줄씩 추가한다: `시각 | 작업 | 결과 | 커밋 해시 | 메모`)

- J1 | 완료 | 003 `91ac503`, 004 `60d0216`, 005 `9fa1e73` | tasks 3개와 analyze-00N 3개. 주요 발견: 003·005 인덱스를 `CREATE INDEX IF NOT EXISTS`가 아니라 `KEY` 절로 넣어야 함(003 A1, 005 C1, MySQL 미지원으로 알고 있음·구현 때 확인), 004 재시도 응답과 모집 완료 후 거부 규칙의 모호성(B1, 사용자 결정 필요), 003 FR-015 자동 테스트 없음(A2)
- J2 | 완료 | `872f9fe` | graphify 노드 519·엣지 907·군집 28, 하위 에이전트 3개(약 37.6만 토큰). 발견 13건(`docs/cross-check-2.md`): 004 거부 문구 불일치(Y1), 008 범위 낡음(Y2), 오류 코드 체계(Y3), 용어집 ↔ 003 `참여 인원 수`(Y7) 등. 그래프는 추적성 감사에 부정확, 스크립트를 기준으로 삼음
- J3 | 완료 | 006 `eb7a68b`, 007 `3969a3f`, 002 `1439827` | 기능별 plan·research·data-model·contracts·quickstart. 결정 총 30개(006 8, 007 10, 002 12) 모두 사용자 결정 대기, 모음은 `docs/overnight/decisions-pending.md`. 002 spec 보완 제안 5건(비밀번호 최대 길이 등)
- J4 | 완료 | (아래 커밋) | Boot 4.1.2/JDK 25, 12개 가정 + 새 발견. 통과 13/13. **중요 발견**: 005 SKIP LOCKED 워커 둘이 REPEATABLE READ에서는 `status` 갱신 UPDATE가 락 대기 시간 초과, READ COMMITTED에서는 통과(결정 대기). 004 직접 UPDATE는 `version=version+1` 필수

## 종료 절차

모든 작업이 `[x]` 또는 `[건너뜀]`이 되면:

1. `docs/overnight/REPORT.md`를 쓴다: 한 일, 건너뛴 일과 사유, 아침에 사용자가 결정할 것(`decisions-pending.md` 링크), 발견한 문제(기존 문서 수정이 필요한 곳), 사용 토큰 대략, 다음 단계 제안.
2. 커밋 후 **다음 깨어남을 예약하지 않고** 끝낸다(`ScheduleWakeup`에 `stop: true`).
3. 아침 확인용으로 `git log --oneline main..overnight/2026-10-26`가 보여야 한다.

## 루프 프롬프트

```
docs/overnight/queue.md를 읽고 '절대 규칙'을 지키며 미완료([ ]) 작업 하나만 수행해. 작업이 끝나면 queue.md의 상태와 로그를 갱신하고 내가 만든 경로만 골라 로컬 커밋해(push 금지). 그다음 다음 작업을 위해 다시 예약해. 미완료 작업이 없으면 종료 절차를 따라 REPORT.md를 쓰고 예약 없이 멈춰. 막히면 사유를 로그에 적고 건너뛰어.
```
