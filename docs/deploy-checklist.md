# 배포 체크리스트 (2026-11-05 작성, 값 미정)

공개 배포 조건은 `docs/architecture.md` 6.5에 있다: 002 병합, SMTP, `ALLOWED_EMAIL_DOMAINS`, `ALLOWED_ORIGINS`. 이 문서는 그 조건을 채우는 데 필요한 항목을 코드(`src/main/resources/application*.yml`, `frontend/`)와 대조해 모은 것이다. **값은 비워 둔다. 저장소가 공개라 시크릿은 환경변수로만 준다.**

## 1. 사용자가 정할 값

| 값 | 쓰이는 곳 | 상태 |
|----|-----------|------|
| 학교 이메일 도메인 | `ALLOWED_EMAIL_DOMAINS` | 미정. 조사와 추천은 아래 1.1. 첫 학교 이름이 필요하다 |
| SMTP 계정(호스트·포트·계정·비밀번호) | `SPRING_MAIL_*` | **Gmail로 결정**(2026-11-18, 아래 2.1). 계정·앱 비밀번호는 사용자가 서버 환경변수로만 넣는다 |
| 개인정보 동의 안내문과 버전 | 프론트 `frontend/src/pages/VerifyPage.tsx`의 `CONSENT_VERSION`(지금 `'v1'`), 안내문 본문 | 초안 `docs/consent-v1.md`(사용자 확인 대기). 버전은 서버에서 20자 이하 |
| 배포 대상과 도메인 | `ALLOWED_ORIGINS`, `VERIFY_LINK_BASE_URL`, 쿠키 Secure | 관제탑 추천은 아래 7(사용자 확인 대기) |
| README 수요 확인 문구 | README(관제탑이 못 고침) | 초안은 아래 8. 수요 확인 인원·응답은 사용자가 채운다 |

### 1.1 이메일 도메인 조사 (2026-11-18)

- `ac.kr`은 고등교육법 등에 따른 국내 대학·교육기관만 등록할 수 있다([tld-list](https://tld-list.com/ko/tld/ac.kr), [KRNIC](https://krnic.or.kr/jsp/eng/domain/policy.jsp)). 다만 교직원과 대학원생도 같은 도메인을 쓴다.
- `.edu`는 미국 인가 고등교육기관용이다([EDUCAUSE](https://net.educause.edu/eligibility)). `skku.edu` 같은 국내 학교는 예외라서 `.edu`를 통째로 열면 대부분 미국 대학이 들어온다.
- 한 학교가 도메인을 여러 개 쓰기도 한다. 예: 성균관대 `skku.edu`(포털 메일)와 `g.skku.edu`(Google 계정)([SKKU IT 서비스](https://www.skku.edu/skku/campus/IT_service/it_service10_1.do)).
- 졸업하면 학교 계정은 학교 규정에 따라 제한된다(예: [서울대 계정 관리지침](https://ist.snu.ac.kr/wp-content/uploads/sites/186/2025/03/서울대학교_계정_관리지침_본문.pdf)). 다만 이미 가입한 계정은 캠공에 그대로 남는다(재인증 없음).
- **결론(추천)**: `ac.kr`·`.edu` 전체 허용은 하지 않는다. 지금 목록·검색에는 학교 구분이 없어서, 여러 학교를 열면 다른 학교·다른 지역 공구가 한 목록에 섞인다. 물건을 기숙사에서 직접 나누는 서비스라 맞지 않는다. **첫 기숙사 학교 하나의 도메인(학생 메일이 쓰는 하위 도메인 포함)만 넣는다.** 학교를 늘리려면 먼저 "학교별 목록" 기능을 스펙으로 만든다.

## 2. 백엔드 환경변수

| 변수 | 기본값(코드) | 운영에서 | 비고 |
|------|--------------|----------|------|
| `DB_URL` | `jdbc:mysql://localhost:3306/camgong?useAffectedRows=true` | **반드시 설정** | `useAffectedRows=true`를 빼면 안 된다. 기동 시 `DatabaseSettingsCheck`가 검사한다 |
| `DB_USER` / `DB_PASSWORD` | `root` / `pw` | **반드시 설정** | 기본값은 로컬용이다. 운영에서 빠뜨리면 로컬 기본값으로 붙으려다 실패한다 |
| `ALLOWED_EMAIL_DOMAINS` | `school.ac.kr` | **반드시 설정** | 기본값은 예시 도메인 |
| `MAIL_SENDER` | `smtp` | 기본값 유지 | `logging`은 local/test/benchmark용. 운영에서 쓰면 메일이 안 나간다 |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | 없음 | **반드시 설정** | 보내는 주소는 `spring.mail.username`이다. 465(SMTPS)면 `SPRING_MAIL_PROTOCOL=smtps`. 587이면 STARTTLS 설정(`SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` 등)이 메일 업체에 따라 필요하다(확인 못 함) |
| `VERIFY_LINK_BASE_URL` | `http://localhost:5173/verify?token=` | **반드시 설정** | 빠뜨리면 인증 메일 링크가 localhost를 가리킨다. 예: `https://<도메인>/verify?token=` |
| `COOKIE_SECURE` | `true` | 기본값 유지 | HTTPS 배포 전제. local 프로필만 false |
| `ALLOWED_ORIGINS` | 빈 값 | 배포 형태에 따라 (아래 3) | 쉼표로 여러 개 |

### 2.1 Gmail SMTP (2026-11-18 결정)

- 서비스 전용 Gmail 계정을 새로 만드는 걸 추천한다(개인 주소가 보낸 사람으로 보이지 않게). 2단계 인증을 켠 뒤 앱 비밀번호를 만든다.
- 환경변수: `SPRING_MAIL_HOST=smtp.gmail.com`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME=<그 Gmail 주소>`, `SPRING_MAIL_PASSWORD=<앱 비밀번호>`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`.
- 일반 Gmail은 하루 발송 수 제한이 있다(정확한 값은 확인 못 함). 기숙사 규모 가입 메일에는 충분할 것으로 본다.
- 실제 발송은 아직 해 보지 않았다.

프로필: 운영은 프로필 없이(기본) 띄운다. `local`은 쿠키 Secure를 끄고 메일을 로그로만 남기므로 운영에서 쓰지 않는다.

## 3. Origin 검사와 프록시

`OriginCheckFilter`는 쓰기 요청의 `Origin`이 **요청의 scheme://Host:port**와 같거나 `ALLOWED_ORIGINS`에 있어야 통과시킨다(Origin 헤더 없는 요청은 통과). 코드에 `server.forward-headers-strategy` 설정이 없다.

- HTTPS를 앞단 프록시에서 끝내고 백엔드가 HTTP로 받으면, 백엔드가 보는 scheme은 `http`라 `https://<도메인>` Origin과 맞지 않는다. 이때는 **`ALLOWED_ORIGINS=https://<도메인>`을 반드시 넣는다**. `server.forward-headers-strategy`는 쓰지 않기로 했다(2026-11-18 사용자 결정 "a": 코드 변경 없이 환경변수로 해결).
- 세션 쿠키는 `SameSite=Lax`다. 프론트와 백엔드가 다른 사이트면 쿠키가 쓰기 요청에 안 실리고 CORS 설정도 없다. **같은 출처 배포(프론트 정적 파일 + `/api` 프록시)를 추천**한다. 다른 출처는 설계를 다시 봐야 한다(확인 못 함).
- 배포 뒤 확인: 같은 출처에서 쓰기 요청 성공, 다른 Origin으로 `curl` 하면 403 `FORBIDDEN_ORIGIN`.

## 4. DB

- MySQL 8.4. 스키마는 앱 기동 때 Flyway가 `src/main/resources/db/migration`을 적용하고 JPA는 `validate`만 한다(2026-11-18 사용자 결정으로 `schema.sql`에서 옮김). 빈 DB에서 시작해야 한다(이력 테이블 없이 테이블만 있는 DB는 Flyway가 기동을 막는다). 스키마 변경은 적용된 파일을 고치지 말고 `V2__…`로 추가한다.
- 앱 계정 권한: 테이블 생성·변경(`CREATE`, `ALTER`, `INDEX`, Flyway 이력 테이블 포함), 읽기·쓰기, **`CREATE TEMPORARY TABLES`**(기동 시 `DatabaseSettingsCheck`가 임시 테이블로 `useAffectedRows`를 검사).
- 세션마다 `innodb_lock_wait_timeout=5`를 앱이 건다(Hikari `connection-init-sql`). DB 쪽 설정은 필요 없다.
- 시간은 UTC로 저장된다(`hibernate.jdbc.time_zone=UTC`). 응답은 +09:00.
- 옛 스키마(B2 이전 `pending_signup` 컬럼)가 남은 DB는 `DatabaseSettingsCheck`가 기동을 막는다. 새 DB로 시작한다.

## 5. 프론트

- 빌드: `frontend/`에서 `npm ci && npm run build`. 결과물(`dist/`)을 정적 호스팅하고 `/api`를 백엔드로 프록시한다(같은 출처).
- `VITE_USE_MOCK`은 **설정하지 않는다**(`'true'`일 때만 목이 켜진다).
- `VITE_API_TARGET`은 dev 서버 프록시용이라 운영 빌드에는 쓰이지 않는다.
- PWA: `vite-plugin-pwa`의 `registerType: 'autoUpdate'`. HTTPS에서만 설치된다. 설치·오프라인은 실기기에서 확인 못 함.
- SPA 라우팅: 정적 호스팅에서 없는 경로(예: `/verify`, `/groupbuys/3`)를 `index.html`로 돌려줘야 한다. 인증 메일 링크가 `/verify?token=`이라 이게 빠지면 가입이 안 된다.

## 6. 배포 직후 확인 순서

1. 기동 로그에 `DatabaseSettingsCheck` 오류가 없는지 본다.
2. 학교 메일로 가입 → 실제 메일 수신 → 링크가 운영 도메인 `/verify`로 열림 → 가입 완료. **실제 SMTP 발송은 아직 한 번도 안 해 봤다.**
3. 로그인 쿠키에 `Secure`·`HttpOnly`가 붙었는지 본다.
4. 개설 → 다른 계정으로 참여 → 상세 확인(쓰기 요청이 403 아님).
5. 다른 Origin으로 쓰기 요청 → 403.
6. 폰에서 홈 화면 추가(PWA).
7. 마감 경과 확정은 실제 마감을 기다려 확인한다(운영 DB를 직접 고치지 않는다).

## 7. 배포 대상 추천 (관제탑, 사용자 확인 대기)

- **서버 한 대 + Docker Compose**: MySQL 8.4, 백엔드 jar, Caddy 세 개를 한 서버에 둔다. Caddy가 HTTPS 인증서를 자동으로 받고, 프론트 정적 파일을 서빙하며 `/api`를 백엔드로 넘긴다. 같은 출처라 쿠키·Origin 문제가 없다. SPA 폴백도 Caddy `try_files` 한 줄이다.
- **서버**: AWS Lightsail 서울 리전, 메모리 2GB 플랜. Spring Boot와 MySQL을 한 대에 올리면 1GB는 빠듯하다. 0원을 원하면 Oracle Cloud 무료 VM도 후보지만 가입·유휴 회수 문제가 있다는 얘기가 있어 1순위로 두지 않았다. 가격은 가입 때 확인한다(확인 못 함).
- **도메인**: 1년 단위로 하나 산다. HTTPS가 있어야 PWA 설치가 된다.
- **백업**: DB가 서버 한 대에만 있으므로 백업 없이 열지 않는다. 매일 `mysqldump`를 서버 밖(예: 객체 저장소)에 보관하거나 서버 자동 스냅샷을 켠다. 복구를 한 번 해 본다.
- Gmail SMTP는 587 포트를 쓴다. 클라우드가 바깥으로 나가는 메일 포트를 막는지는 업체별로 확인한다(확인 못 함).
- 결정되면 `Dockerfile`·`compose.yaml`·`Caddyfile`은 작업 세션이 만들고 관제탑이 검수한다. 시크릿은 서버의 `.env`에만 둔다.

## 8. README 문구 초안 (사용자가 넣는다)

`<!-- TODO(사용자) ... -->` 자리에 넣을 문구다. `[ ]`는 사용자가 채운다(관제탑이 지어내지 않는다). design-plan 단계 0에 남은 기록은 "기숙사생 지인 의견 '쓸만하다'"뿐이다.

> 기숙사생 지인 [N]명에게 물어보니 [응답 요약, 예: "댓글로 수량 세는 게 제일 귀찮다"]는 반응이었다. 당근 '같이사요'는 동네 이웃을 **사람 수**로 모으는 모집 글이다(2022년 출시 당시 최대 4명). 캠공은 같은 학교 학생끼리 한 상품을 **수량으로 나눠 갖는 과정**(남은 수량 경쟁, 다 차면 자동 완료, 입금·수령 체크)을 다룬다.

- 같이사요 내용은 2022년 출시 기사 기준이다([이투데이](https://www.etoday.co.kr/news/view/2154955)). 지금 제한은 확인 못 함.
- "기숙사생 인증"이라고 쓰지 않는다. 학교 메일 인증은 재학 여부만 확인하고 기숙사 거주는 확인하지 않는다.

README 상단의 "상태: 설계 단계. 코드는 아직 없고" 문구도 지금은 사실과 다르다(구현·검수 끝남). 같이 고친다.

## 확인 못 한 것

- 메일 업체별 SMTP 세부 설정(STARTTLS, 발신 주소 정책).
- 프록시 뒤 실제 배포에서의 Origin·쿠키 동작.
- 운영 규모 성능(측정은 모두 로컬 한 기계).
