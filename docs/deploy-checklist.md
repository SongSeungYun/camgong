# 배포 체크리스트 (2026-11-05 작성, 값 미정)

공개 배포 조건은 `docs/architecture.md` 6.5에 있다: 002 병합, SMTP, `ALLOWED_EMAIL_DOMAINS`, `ALLOWED_ORIGINS`. 이 문서는 그 조건을 채우는 데 필요한 항목을 코드(`src/main/resources/application*.yml`, `frontend/`)와 대조해 모은 것이다. **값은 비워 둔다. 저장소가 공개라 시크릿은 환경변수로만 준다.**

## 1. 사용자가 정할 값

| 값 | 쓰이는 곳 | 상태 |
|----|-----------|------|
| 학교 이메일 도메인 | `ALLOWED_EMAIL_DOMAINS` | 미정 |
| SMTP 계정(호스트·포트·계정·비밀번호) | `SPRING_MAIL_*` | 미정 |
| 개인정보 동의 안내문과 버전 | 프론트 `frontend/src/pages/VerifyPage.tsx`의 `CONSENT_VERSION`(지금 `'v1'`), 안내문 본문 | 미정. 버전은 서버에서 20자 이하 |
| 배포 대상과 도메인 | `ALLOWED_ORIGINS`, `VERIFY_LINK_BASE_URL`, 쿠키 Secure | 미정 |
| README 수요 확인 문구 | README(관제탑이 못 고침) | 미정 |

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

프로필: 운영은 프로필 없이(기본) 띄운다. `local`은 쿠키 Secure를 끄고 메일을 로그로만 남기므로 운영에서 쓰지 않는다.

## 3. Origin 검사와 프록시

`OriginCheckFilter`는 쓰기 요청의 `Origin`이 **요청의 scheme://Host:port**와 같거나 `ALLOWED_ORIGINS`에 있어야 통과시킨다(Origin 헤더 없는 요청은 통과). 코드에 `server.forward-headers-strategy` 설정이 없다.

- HTTPS를 앞단 프록시에서 끝내고 백엔드가 HTTP로 받으면, 백엔드가 보는 scheme은 `http`라 `https://<도메인>` Origin과 맞지 않는다. 이때는 **`ALLOWED_ORIGINS=https://<도메인>`을 반드시 넣는다**(또는 `server.forward-headers-strategy` 설정은 백엔드 세션 몫이라 결정 필요).
- 세션 쿠키는 `SameSite=Lax`다. 프론트와 백엔드가 다른 사이트면 쿠키가 쓰기 요청에 안 실리고 CORS 설정도 없다. **같은 출처 배포(프론트 정적 파일 + `/api` 프록시)를 추천**한다. 다른 출처는 설계를 다시 봐야 한다(확인 못 함).
- 배포 뒤 확인: 같은 출처에서 쓰기 요청 성공, 다른 Origin으로 `curl` 하면 403 `FORBIDDEN_ORIGIN`.

## 4. DB

- MySQL 8.4. 스키마는 앱 기동 때 `schema.sql`(`CREATE TABLE IF NOT EXISTS`)로 만들고 JPA는 `validate`만 한다. 스키마가 바뀌면 기존 테이블은 고쳐지지 않는다. 운영 데이터가 생기기 전에 Flyway로 옮길지 정해야 한다(`schema.sql` 첫 줄의 메모).
- 앱 계정 권한: 테이블 생성(`CREATE`), 읽기·쓰기, **`CREATE TEMPORARY TABLES`**(기동 시 `DatabaseSettingsCheck`가 임시 테이블로 `useAffectedRows`를 검사).
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

## 확인 못 한 것

- 메일 업체별 SMTP 세부 설정(STARTTLS, 발신 주소 정책).
- 프록시 뒤 실제 배포에서의 Origin·쿠키 동작.
- 운영 규모 성능(측정은 모두 로컬 한 기계).
