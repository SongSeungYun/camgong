# 배포 체크리스트 (2026-11-05 작성, 값 미정)

공개 배포 조건은 `docs/architecture.md` 6.5에 있다: 002 병합, SMTP, 허용 대학 도메인(009부터 `EXTRA_SCHOOL_DOMAINS`, 아래 1.1·1.2), `ALLOWED_ORIGINS`. 이 문서는 그 조건을 채우는 데 필요한 항목을 코드(`src/main/resources/application*.yml`, `frontend/`)와 대조해 모은 것이다. **값은 비워 둔다. 저장소가 공개라 시크릿은 환경변수로만 준다.**

## 1. 사용자가 정할 값

| 값 | 쓰이는 곳 | 상태 |
|----|-----------|------|
| 대학 이메일 도메인 | `EXTRA_SCHOOL_DOMAINS`(009) | **결정(2026-11-19): 전국 대학.** `ac.kr` 메일은 설정 없이 모두 가입된다. `ac.kr`이 아닌 국내 대학(예: `skku.edu`)만 `키=도메인,도메인;…` 형식으로 넣는다. 키는 한 번 쓰면 바꾸지 않는다. **회원이 생긴 뒤 묶음을 바꾸면(예: 이미 가입자가 있는 `sg.ac.kr`을 `sogang.ac.kr` 묶음에 넣음) 기존 회원과 새 회원이 다른 학교가 된다.** 학교는 가입 확인 때 한 번만 정하기 때문이다. 바꿀 때는 그 도메인 회원의 `member.school`과 그 회원들이 연 `group_buy.school`을 같이 고친다(절차는 확인 못 함). `co.kr`처럼 누구나 받는 메일 도메인은 넣지 않는다(설정 검사는 `ac.kr`만 막는다). 스펙 `specs/009-school-scope` |
| SMTP 계정(호스트·포트·계정·비밀번호) | `SPRING_MAIL_*` | **Gmail로 결정**(2026-11-18, 아래 2.1). 계정·앱 비밀번호는 사용자가 서버 환경변수로만 넣는다 |
| 개인정보 동의 안내문과 버전 | 프론트 `frontend/src/pages/VerifyPage.tsx`의 `CONSENT_VERSION`(지금 `'v1'`), 안내문 본문 | 초안 `docs/consent-v1.md`(사용자 확인 대기). 버전은 서버에서 20자 이하 |
| 배포 대상과 도메인 | `DOMAIN`(compose가 `ALLOWED_ORIGINS`·`VERIFY_LINK_BASE_URL`·`RESET_LINK_BASE_URL`을 만든다), 쿠키 Secure | 도메인은 `camgong.app`(2026-11-23 가비아 구매). 서버는 Vultr 서울 1GB(아래 7) |
| README 수요 확인 문구 | README(관제탑이 못 고침) | 초안은 아래 8. 수요 확인 인원·응답은 사용자가 채운다 |

### 1.1 이메일 도메인 조사 (2026-11-18)

- `ac.kr`은 고등교육법 등에 따른 국내 대학·교육기관만 등록할 수 있다([tld-list](https://tld-list.com/ko/tld/ac.kr), [KRNIC](https://krnic.or.kr/jsp/eng/domain/policy.jsp)). 다만 교직원과 대학원생도 같은 도메인을 쓴다.
- `.edu`는 미국 인가 고등교육기관용이다([EDUCAUSE](https://net.educause.edu/eligibility)). `skku.edu` 같은 국내 학교는 예외라서 `.edu`를 통째로 열면 대부분 미국 대학이 들어온다.
- 한 학교가 도메인을 여러 개 쓰기도 한다. 예: 성균관대 `skku.edu`(포털 메일)와 `g.skku.edu`(Google 계정)([SKKU IT 서비스](https://www.skku.edu/skku/campus/IT_service/it_service10_1.do)).
- 졸업하면 학교 계정은 학교 규정에 따라 제한된다(예: [서울대 계정 관리지침](https://ist.snu.ac.kr/wp-content/uploads/sites/186/2025/03/서울대학교_계정_관리지침_본문.pdf)). 다만 이미 가입한 계정은 캠공에 그대로 남는다(재인증 없음).
- **(지난 결론, 2026-11-19 009로 대체: `ac.kr` 전체 + 같은 학교끼리만. 위 표 1행)** 결론(추천): `ac.kr`·`.edu` 전체 허용은 하지 않는다. 지금 목록·검색에는 학교 구분이 없어서, 여러 학교를 열면 다른 학교·다른 지역 공구가 한 목록에 섞인다. 물건을 기숙사에서 직접 나누는 서비스라 맞지 않는다. **첫 기숙사 학교 하나의 도메인(학생 메일이 쓰는 하위 도메인 포함)만 넣는다.** 학교를 늘리려면 먼저 "학교별 목록" 기능을 스펙으로 만든다.

### 1.2 전국 대학 대상일 때 남들이 하는 방식 (2026-11-19 조사, 사용자 "전국대학을 대상으로 하고싶은데")

| 방식 | 예 | 장단점 |
|------|----|--------|
| 도메인 목록(학교 이름 매핑, 하위 도메인 포함, 악용 도메인 차단 목록) | JetBrains [swot](https://github.com/JetBrains/swot)(GitHub Education 학생 인증의 기반), [Hipo university-domains-list](https://github.com/Hipo/university-domains-list), 한국판 [k-emails](https://github.com/ruk13xa/k-emails)(swot 일부 사용). 모두 MIT | 이메일 → 학교 이름을 알 수 있다. 목록을 계속 관리해야 하고 빠진 학교가 생긴다 |
| 접미사 규칙(`*.ac.kr` 통째로) | Cursor 포럼에 `.ac.kr` 허용 요청이 올라온 사례([링크](https://forum.cursor.com/t/add-support-for-ac-kr-domains-south-korea-to-student-verification-allowlist/144661/2)) | 목록 관리가 없다. `ac.kr`은 대학·교육기관만 등록 가능해서 국내에선 꽤 정확하다. `.edu` 국내 학교(`skku.edu` 등)는 따로 넣어야 한다 |
| 학교별 인증 수단(학생증, 포털, 재학증명서) | 에브리타임(학교마다 수단이 다름) | 가장 정확하지만 운영 비용이 크다. MVP 범위 밖 |

공공데이터 [경상남도교육청_대학정보](https://www.data.go.kr/data/15150589/fileData.do)는 전국 439개 대학 이름·홈페이지는 있지만 이메일 도메인 열은 없다.

**관제탑 추천**: 접미사 규칙 `ac.kr` + 국내 `.edu` 학교 몇 개를 명시 목록으로. 학교 구분은 메일 도메인의 `<학교>.ac.kr` 부분(예: `cs.snu.ac.kr` → `snu.ac.kr`)으로 정한다. 학교 구분 없이 열면 목록에 전국 공구가 섞이므로 **같은 학교끼리만 보고 참여하는 기능이 함께 필요하다**(003·004 스펙 변경). → 결정(2026-11-19 사용자 "2번으로가고"), `specs/009-school-scope`로 구현·머지됨.

## 2. 백엔드 환경변수

| 변수 | 기본값(코드) | 운영에서 | 비고 |
|------|--------------|----------|------|
| `DB_URL` | `jdbc:mysql://localhost:3306/camgong?useAffectedRows=true` | **반드시 설정** | `useAffectedRows=true`를 빼면 안 된다. 기동 시 `DatabaseSettingsCheck`가 검사한다 |
| `DB_USER` / `DB_PASSWORD` | `root` / `pw` | **반드시 설정** | 기본값은 로컬용이다. 운영에서 빠뜨리면 로컬 기본값으로 붙으려다 실패한다 |
| `EXTRA_SCHOOL_DOMAINS` | 빈 값 | 필요할 때만 | 009부터. 빈 값이면 `ac.kr` 규칙만 쓴다. 예: `skku.edu=skku.edu,g.skku.edu`. 형식이 틀리거나 묶음끼리 도메인이 겹치면 기동이 실패한다. 옛 `ALLOWED_EMAIL_DOMAINS`는 009부터 **무시된다**(넣어도 학교가 제한되지 않는다) |
| `MAIL_SENDER` | `smtp` | 기본값 유지 | `logging`은 local/test/benchmark용. 운영에서 쓰면 메일이 안 나간다 |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | 없음 | **반드시 설정** | 보내는 주소는 `spring.mail.username`이다. 465(SMTPS)면 `SPRING_MAIL_PROTOCOL=smtps`. 587이면 STARTTLS 설정(`SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` 등)이 메일 업체에 따라 필요하다(확인 못 함) |
| `VERIFY_LINK_BASE_URL` | `http://localhost:5173/verify?token=` | **반드시 설정** | 빠뜨리면 인증 메일 링크가 localhost를 가리킨다. 예: `https://<도메인>/verify?token=` |
| `RESET_LINK_BASE_URL` | `http://localhost:5173/password-reset/confirm?token=` | **반드시 설정** | 011. 빠뜨리면 재설정 메일 링크가 localhost를 가리킨다. 예: `https://<도메인>/password-reset/confirm?token=` |
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

## 7. 배포 대상 (서버·도메인 결정됨)

- **서버 한 대 + Docker Compose**: MySQL 8.4, 백엔드 jar, Caddy 세 개를 한 서버에 둔다. Caddy가 HTTPS 인증서를 자동으로 받고, 프론트 정적 파일을 서빙하며 `/api`를 백엔드로 넘긴다. 같은 출처라 쿠키·Origin 문제가 없다. SPA 폴백도 Caddy `try_files` 한 줄이다.
- **서버**: **결정(2026-11-24): Vultr 서울, Cloud Compute(Regular Performance) 1 vCPU·1GB·25GB SSD 월 $5, Ubuntu 26.04 LTS(2026-11-25 생성, 지원 2031-04까지).** 확인함: 위치 Seoul, `Ubuntu 26.04.1 LTS`, 루트 디스크 23G(6.1G 사용), 공인 IPv4가 `enp1s0`에 바로 붙음, Auto Backups Not Enabled, SSH 키 접속(방화벽 그룹 `camgong`). 처음 실수로 만든 Atlanta·26.10 서버는 지웠고, 월 청구 $5를 확인했다(2026-11-25 사용자). 메모리가 모자라면 같은 서버를 2GB(월 $10)로 올린다(Vultr는 올리기만 되고 내리기는 안 된다). Vultr 자동 백업은 쓰지 않는다(보관 위치·기간 확인 못 함, 백업은 아래 덤프로). 버린 후보: Azure for Students(2026-11-23 골랐으나 학교 메일 인증 뒤 "무료 계정 자격 없음"으로 거절, 새 Microsoft 계정도 같음, 원인 확인 못 함), AWS Lightsail 2GB 월 $12(기존 계정이라 새 계정 크레딧 대상 아님), Oracle 무료(2026년 춘천 A1 비활성·유휴 회수), 네이버 Micro(디스크 10GB, 공인 IP 별도).
  - **1GB 메모리**: 로컬 MySQL 컨테이너가 기본 설정으로 531MB를 쓴다(2026-11-23). 013에서 한도를 정하고 로컬 측정을 통과했다(2026-11-25, 백엔드 PR #37 `9f25801`).
    - 한도: mysql 288m(`deploy/mysql/camgong.cnf`), backend 460m(JVM 힙 224m 고정), web 128m. 합은 876m다.
    - 서버에 스왑 2GB를 붙이고, 이미지는 서버 밖에서 빌드한다.
    - 측정 기록은 `docs/benchmarks/one-gb-server.md`에 있다. 서버에서 할 확인은 7.1-4-1이다.
  - **포트**: Vultr 방화벽 그룹으로 연다. 인바운드는 80·443(TCP)·443(UDP)과 SSH 22(내 IP만). 서버 안의 ufw는 Docker가 연 포트를 막지 못하므로(Docker가 iptables 규칙을 직접 넣는다) 방화벽 그룹이 기준이다. Vultr는 바깥으로 나가는 25만 막고 587(Gmail SMTP)은 막지 않는다.
- **도메인**: 1년 단위로 하나 산다. HTTPS가 있어야 PWA 설치가 된다. 2026-11-22 조회: `camgong.com`·`camgong.co.kr`은 이미 남이 등록했고, `camgong.kr`·`camgong.app`은 비어 있다. 관제탑 추천은 Cloudflare에서 `camgong.app`(첫해 약 $8.20, 갱신 약 $14.20, 가격은 비공식 집계 기준이라 결제 화면에서 확인). Cloudflare Registrar는 Cloudflare DNS를 써야 한다. Caddy가 인증서를 받게 레코드는 프록시 끔(DNS only)으로 둔다. `.kr`은 카페24·가비아에서 첫해 약 1.5만원·갱신 약 2.2~2.4만원이고, 국내 주소가 필요하며 whois에 이름·주소가 공개된다(비공개 서비스 필요). **결정(2026-11-23): 가비아에서 `camgong.app` 구매.** `.env`의 `DOMAIN=camgong.app`. 유료 SSL 인증서는 사지 않는다(Caddy가 Let's Encrypt로 받는다). 가비아 DNS 관리툴에 `camgong.app` A 레코드(호스트 `@`, TTL 600)를 Vultr 서버 IP로 넣었다(2026-11-25, 가비아 네임서버 3개와 1.1.1.1·8.8.8.8에서 조회 확인). AAAA·`www`는 없다. 가비아 부가서비스(포워딩·파킹·도메인 연결)는 .app이라 쓸 수 없고 쓰지 않는다. 자동 갱신과 whois 정보 비공개는 사용자가 가비아에서 확인한다(확인 못 함).
- **백업**: DB가 서버 한 대에만 있으므로 백업 없이 열지 않는다. 매일 `mysqldump`를 서버 밖(예: 객체 저장소)에 보관하거나 서버 자동 스냅샷을 켠다. 복구를 한 번 해 본다. **어느 쪽이든 7일이 지나면 지워지게 한다**(동의문 "백업과 인증 메일 발송 기록에서는 7일 안에 지워집니다", 2026-11-19 사용자 "7일"). 서버 스냅샷과 서버 디스크 덤프(7.1-5)를 같이 쓰면 스냅샷 안에 최대 7일 된 덤프가 들어가 약 13일 남는다. 스냅샷을 켜면 덤프 디렉터리를 빼거나 둘 중 하나만 쓴다.
- Gmail SMTP는 587 포트를 쓴다. Vultr는 587을 막지 않는다(위 서버 항목).
- 결정되면 `Dockerfile`·`compose.yaml`·`Caddyfile`은 작업 세션이 만들고 관제탑이 검수한다. 시크릿은 서버의 `.env`에만 둔다.

### 7.1 운영 절차 (`deploy/`, 2026-11-19 백엔드 세션 초안 반영)

1. 서버 준비(처음 한 번, 사용자가 SSH로 한다). 방화벽 그룹과 DNS A 레코드는 이미 됐다(7). 아래 명령은 서버에서 돌려 보지 않았다(확인 못 함).
   - 스왑 2GB(013 plan 결정 3):
     - 먼저 `swapon --show`로 이미 있는지 본다.
     - 없으면 `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile && echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab`.
     - 그다음 `echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-camgong.conf && sudo sysctl --system`.
     - `free -m`의 Swap이 약 2047인지 본다. Docker를 띄우기 전의 `free -m` used 값도 적어 둔다(OS 몫).
   - Docker: 공식 문서(docs.docker.com/engine/install/ubuntu)의 apt 저장소 방식으로 `docker-ce`·`docker-compose-plugin`을 설치한다. 26.04용 저장소(`resolute`)가 있다(2026-11-25 download.docker.com에서 확인). 설치 뒤 `docker compose version`을 본다.
   - 작업 계정: root 대신 쓴다.
     - `adduser deploy && usermod -aG sudo,docker deploy` → `rsync --archive --chown=deploy:deploy ~/.ssh /home/deploy`.
     - **root 창을 닫지 말고** 새 터미널에서 `ssh deploy@<서버>` → `sudo true`가 되는지 본다.
     - 된 뒤에 `/etc/ssh/sshd_config.d/99-camgong.conf`에 `PermitRootLogin no`와 `PasswordAuthentication no`를 적고 `sudo systemctl reload ssh`.
     - `docker` 그룹은 root와 같은 권한이다.
     - 집 IP가 바뀌면 SSH가 막힌다. Vultr 방화벽 그룹의 22 규칙을 고치거나, Vultr 웹 콘솔로 들어간다.
2. `sudo mkdir -p /srv/camgong && sudo chown deploy: /srv/camgong && git clone https://github.com/SongSeungYun/camgong /srv/camgong/repo` → `cd /srv/camgong/repo/deploy && cp .env.example .env` → 값 채우기 → `chmod 600 .env`. `ALLOWED_ORIGINS`·`VERIFY_LINK_BASE_URL`·`RESET_LINK_BASE_URL`은 compose가 `DOMAIN`으로 만든다. 장부 디렉터리는 6번에 적힌 대로 같이 만든다.
3. 이미지는 서버에서 빌드하지 않는다(013 plan 결정 4, 1GB에서 Gradle·Vite 빌드는 메모리가 모자란다).
   - 맥(백엔드 세션이 할 수 있다)에서 빌드하고 묶는다: `cd deploy && DOCKER_DEFAULT_PLATFORM=linux/amd64 docker compose build && docker save camgong/backend:latest camgong/web:latest | gzip > camgong-images.tar.gz`.
     - compose가 `.env`와 필수 값을 읽으므로 맥에는 가짜 값의 임시 `deploy/.env`를 두고, 끝나면 지운다.
     - web 빌드는 처리방침 자리표시 4개가 남아 있으면 일부러 멈춘다.
   - 옮기기: `scp camgong-images.tar.gz deploy@<서버>:/srv/camgong/`.
   - 서버에서 띄우기: `docker load -i /srv/camgong/camgong-images.tar.gz` → `docker image inspect -f '{{.Architecture}}' camgong/backend:latest`가 `amd64`인지 본다 → `cd /srv/camgong/repo/deploy && docker compose up -d --no-build`.
   - `docker compose logs backend`에서 Flyway 적용과 `Started`를 본 뒤 아래 §6 순서로 확인한다.
   - backend·web은 `pull_policy: never`다. `camgong/*` 이름은 Docker Hub의 남의 계정으로도 풀리기 때문이다. load를 빠뜨리면 `No such image`로 멈추는 게 정상이다. `docker compose pull`은 `mysql:8.4`만 받는다.
4. 업데이트:
   - compose·Caddyfile·cnf가 바뀌었으면 서버에서 `git pull`.
   - 맥에서 3번대로 빌드해 옮긴 뒤, 서버에서 `docker load -i …` → `docker compose up -d --no-build`. 백엔드가 다시 뜨는 몇 초 동안은 요청이 실패한다.
   - 태그가 빠진 옛 이미지는 가끔 `docker image prune`으로 지운다(태그 없는 것만 지운다).
4-1. 메모리 확인(013): 배포 직후, 며칠 뒤, 백업(5) 도중에 본다. `deploy/`에서 돌린다.
   - `free -m`, `swapon --show`, `docker stats --no-stream`
   - `docker inspect -f '{{.Name}} oom={{.State.OOMKilled}} restarts={{.RestartCount}}' $(docker compose ps -q)`
   - `for s in mysql backend web; do echo "== $s"; docker compose exec -T $s sh -c 'cat /sys/fs/cgroup/memory.peak /sys/fs/cgroup/memory.swap.current; grep oom_kill /sys/fs/cgroup/memory.events'; done`
     - mysql·caddy 이미지에서 같은 명령이 되는 것은 로컬에서 확인했다.
   - 통과: `oom=false`, `restarts=0`, `oom_kill 0`.
   - `free`의 Swap used와 컨테이너 `memory.swap.current`가 배포 직후보다 며칠 뒤에 계속 늘어 있으면, 2GB(월 $10) 전환을 사용자에게 묻는다(013 plan 결정 6).
   - backend `memory.peak`가 한도(460m)에 닿는 건 측정에서도 그랬다(페이지 캐시를 비우며 돈다). 볼 것은 스왑이다.
5. 백업: crontab에 `0 4 * * * /경로/deploy/backup.sh`. 7일이 지난 덤프는 스크립트가 지운다(010 T017). **덤프가 실패한 날은 지우지 않는다**(남은 백업을 지키려고). 실패가 이어지면 7일을 넘겨 남는다. 그래서 healthchecks.io(무료)로 알아챈다(2026-11-22 사용자 선택, 010 plan 결정 5-3): 서비스 Gmail로 가입 → 주기 1일·유예 1시간 확인을 하나 만든다 → 그 주소를 `.env`의 `HC_PING_URL`에 넣는다. 스크립트가 성공하면 호출하고 실패하면 `/fail`을 호출한다. 호출이 끊기면(cron이 안 돎, 서버가 죽음) 메일이 온다(010 T036). 서버 밖 보관은 아직 TODO이고, 두게 되면 같은 7일 수명 규칙을 건다. **서버 밖 보관 전에는 서버가 사라지면 백업도 사라진다.** MySQL binlog는 compose에서 끈다(탈퇴 전 값이 30일 남기 때문, 010 plan 결정 5-1). binlog를 켠 채 돌던 볼륨이면 이미 남은 `binlog.*`는 끈 뒤에도 지워지지 않는다. 끄기 전에 `RESET BINARY LOGS AND GTIDS`를 한 번 실행한다. 새 볼륨으로 시작하면 필요 없다.
6. 복구: 백엔드 중지(`docker compose stop backend`) → `gunzip -c <파일> | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot camgong'` → 백엔드 시작 → 로그에서 `reanonymized=N` 확인 → 5분 뒤 `retention-cleanup groupBuys=N members=M` 확인(012: 덤프에 남아 있던, 끝난 지 1년 지난 공구가 다시 지워진다). 장부 준비(처음 한 번, 2단계와 같이): `.env`의 `WITHDRAW_LEDGER_DIR`에 호스트 절대 경로(예: `/srv/camgong/ledger`)를 넣고 `mkdir -p /srv/camgong/ledger && sudo chown 10001 /srv/camgong/ledger`(백엔드는 uid 10001로 돈다). 비어 있거나 쓸 수 없으면 백엔드가 뜨지 않는다. **복구하면 백업 시점 뒤의 탈퇴가 되살아난다.** 앱이 탈퇴 장부(컨테이너 밖 `WITHDRAW_LEDGER` 파일, 8일 보관)를 기동 때 읽어 그 회원들을 다시 익명화한다(2026-11-22 사용자 선택 "장부 파일 + 자동 재적용", 010 plan 결정 5-2, T032~T037). WARN에 OPEN 공구 id가 나오면 그 공구를 확인한다(되살아난 상태에서 탈퇴자가 개설자·참여자인 공구). 장부도 서버 디스크에 있어서 서버째 잃으면 같이 사라진다. 서버 밖 백업을 둘 때 장부도 함께 옮길 방법을 정한다. 장부가 생긴 뒤에는 docker 로그 회전을 건다(가린 수신 주소가 기한 없이 남는 문제도 같이 풀린다, T035).
6-1. Gmail 보낸편지함(서비스 계정): SMTP로 보낸 인증·재설정 메일이 자동 저장되고, 반송 메일은 받은편지함에 쌓인다. 서비스 계정의 Apps Script가 매일 6일 지난 보낸 메일·반송 메일을 휴지통을 거치지 않고 지운다. 설정 순서와 코드는 `docs/gmail-cleanup.md`. 돌려 보지는 않았다(확인 못 함).
6-2. 개인정보 처리방침: 배포 전에 만들어 공개한다. 보호책임자 연락처는 서비스 Gmail로 한다(2026-11-19 사용자 선택 "동의문엔 빼고 처리방침엔 서비스 메일"). 비밀번호를 잊은 회원은 학교 메일로 비밀번호를 재설정한 뒤 직접 탈퇴한다(2026-11-22 사용자 "다시 본인인증하면되지", 선택 "비밀번호 재설정", `specs/011-password-reset`). 학교 메일을 더는 받을 수 없는 회원(졸업 등)은 본인 확인 수단이 없다. 처리방침에 그렇게 적는다.
7. `docker compose down -v`는 쓰지 않는다. 볼륨(`mysql-data`, `caddy-data`)이 지워진다.
8. `DB_USER`/`DB_PASSWORD`/`MYSQL_ROOT_PASSWORD`는 볼륨을 처음 만들 때만 쓰인다. 나중에 `.env`를 바꿔도 DB 계정은 바뀌지 않는다.

로컬 확인(백엔드 세션, `DOMAIN=localhost`): 목록 200, 깊은 경로 index.html, 없는 자산 404, http→https 308, 다른 Origin 403, 백업 파일 복구까지 됐다. 메모리는 백엔드 약 480MiB, MySQL 약 485MiB, Caddy 15MiB(16GB 맥 기준, 013 한도 전, 실서버 확인 못 함). 한도를 건 측정은 `docs/benchmarks/one-gb-server.md`. 실제 SMTP 발송은 확인 못 함.

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
- 1GB 서버의 실제 메모리 여유(OS·dockerd 몫)와 1 vCPU에서의 속도. 7.1-4-1로 확인하고, 서버 k6 재측정 방법은 서버가 준비되면 정한다.
