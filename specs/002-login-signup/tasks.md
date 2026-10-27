---

description: "회원 가입·로그인·로그아웃 구현 태스크"
---

# Tasks: 회원 가입·로그인·로그아웃

**Input**: `/specs/002-login-signup/` 의 plan.md, spec.md, research.md, data-model.md, contracts/auth-api.md, quickstart.md

**Prerequisites**: plan.md, spec.md. **001 구현이 끝나 있어야 한다**: `Member`/`MemberRepository`(최소 형태), `ApiException`/`ApiExceptionHandler`, `IntegrationTestBase`(`MutableClock`, `as(memberId)`), `GroupBuyController`의 임시 `X-Member-Id`. 003~007은 필요 없다(이후 기능은 이 기능의 `CurrentMember`를 쓴다).

**Tests**: 이 기능에는 헌법 IV 필수 대상이 없지만, 보안·한도·잠금·토큰 단일 사용은 통합 테스트로 덮는다(plan Constitution Check). 각 스토리에서 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.

**Organization**: 사용자 스토리별로 묶었다. 이 프로젝트는 Spec Kit의 `implement` 명령은 쓰지 않는다. 구현은 작업 세션(AI)이 태스크 단위로 하고, 사용자가 승인한 뒤에 커밋한다(`docs/sessions/README.md`). **(핵심 로직)** 표시 태스크는 승인 요약에 설명 체크 질문이 붙는다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 태스크에 의존하지 않아 병렬 가능
- **[Story]**: US1(가입·인증), US2(로그인·로그아웃·보호), US3(학교 이메일 검사·재발송·재제출), US4(로그인 잠금)
- 경로의 `main/`은 `src/main/java/com/camgong/`, `test/`는 `src/test/java/com/camgong/`, `res/`는 `src/main/resources/` 를 뜻한다. 이 기능의 새 코드는 패키지 `auth/`에 둔다.
- 승인 요약에 **새 의존성**(T002)과 **측정값**(T038)은 따로 적는다.

## 이 tasks가 정한 것 (사용자 확인 필요, 2026-10-27)

문서끼리 어긋나거나 비어 있던 곳을 아래처럼 정해 두었다. 맞지 않으면 이 표만 고치면 된다.

| # | 정한 것 | 이유 |
|---|---------|------|
| a | `pending_signup`에 `used_at DATETIME(6) NULL`을 더하고, 인증 성공 시 행을 **지우지 않고** `used_at`을 채운다 | 계약이 `TOKEN_USED`(409)와 `INVALID_TOKEN`(400)을 구분하는데, 사용 시 행을 지우면 둘을 구분할 수 없다. 이미 인증된 이메일은 `member` 조회가 먼저 막으므로 행이 남아도 충돌이 없다. 정리 작업(T037)이 오래된 행을 지운다 |
| b | 인증을 마치지 않은 이메일의 로그인은 `401 INVALID_CREDENTIALS`로 **존재하지 않는 계정과 똑같이** 답한다. `EMAIL_NOT_VERIFIED`는 쓰지 않는다 | FR-010은 안내를 "할 수 있다"일 뿐이고, 구분하면 가입 신청 여부가 새어 나간다. "인증이 필요하면 가입 때 받은 메일을 확인하라"는 문구는 프론트 로그인 화면에 고정으로 둔다(008) |
| c | `Origin`과 `Referer`가 **둘 다 없는** 상태 변경 요청은 허용한다 | 브라우저는 상태 변경 요청에 `Origin`을 보낸다. 없는 쪽은 curl·테스트 같은 비브라우저이고, 쿠키는 `SameSite=Lax`가 한 번 더 막는다 |
| d | 같은 신청이 동시에 와서 하나가 먼저 INSERT되면 나머지는 `429 TOO_MANY_REQUESTS`다 | FR-015: 회원 정보 하나, 메일 1통. 한도 규칙(1분 간격)과 같은 응답이라 규칙이 하나로 합쳐진다 |
| e | 동의(`consent`, `consentVersion`)는 FR-017로 data-model·contract·spec에 있다. **변경 B2(2026-10-28)로 동의는 신청이 아니라 `POST /verify`에서 받는다**(아래 블록) | 계정 선점 방지 |

## 변경 B2 (2026-10-28): 신청은 이메일만, 이름·비밀번호·동의는 인증 단계에서

사용자가 "안 2"를 결정했다. 공격자가 먼저 피해자의 학교 이메일로 자기 비밀번호를 넣어 신청해 두면, 피해자가 인증하는 순간 공격자가 아는 비밀번호의 계정이 되는 계정 선점(pre-hijacking)을 막기 위해서다. 이전 "안 B(재제출은 이름·비밀번호 유지)"는 폐기한다. 근거: spec.md 2026-10-28 Clarification·FR-001/002/003/006/017, research.md 결정 13, contracts/auth-api.md, data-model.md, quickstart.md. **아래 T001~T040은 이미 구현되어 있으므로 ID·체크박스는 바꾸지 않는다. 구현 세션은 이 블록만 보고 후속 수정을 한다(새 태스크 ID는 만들지 않는다).** 아래 각 항목의 "변경 전" 서술이 본문 태스크에 남아 있어도 이 블록이 우선한다.

**새 동작 요약**

- `POST /api/auth/signup` 본문은 `{ "email" }` 하나. 검증: 이메일 형식, 허용 도메인. 이미 `member`에 있으면 409 `ALREADY_REGISTERED`, 한도(1분 간격, 24시간 5회)면 429(`Retry-After`, `retryAt`). 성공 202 `{ email, resendAvailableAt }`. 같은 인증 전 이메일로 다시 제출하면 토큰·한도만 갱신하고 새 메일(옛 토큰 무효).
- `pending_signup`은 `id, email_key, email, token_hash, token_expires_at, last_sent_at, window_started_at, sent_count, used_at, created_at`만 남긴다(`display_name`, `password_hash`, `consent_at`, `consent_version` 컬럼 제거).
- `POST /api/auth/verify` 본문은 `{ token, displayName, password, consent: true, consentVersion }` 전부 필수. 순서: ① 토큰 조회(400 `INVALID_TOKEN` / 409 `TOKEN_USED` / 410 `TOKEN_EXPIRED`) → ② 본문 검증 오류(표시 이름 2~20자·공백만 불가, 비밀번호 8자 이상·UTF-8 72바이트 이하, `consent`가 `true`, `consentVersion` 존재)면 400 `errors[]`이고 **토큰은 소진되지 않는다** → ③ `used_at` 조건부 `UPDATE` 선점(영향 행 0이면 409 `TOKEN_USED`) → ④ BCrypt 해시로 `member` INSERT(`name`, `display_name_key`, `email`, `email_key`, `password_hash`, `consent_at = now`, `consent_version`, `created_at`) → ⑤ 이름 충돌 409 `DISPLAY_NAME_TAKEN`, 이메일 충돌 409 `ALREADY_REGISTERED`이면 같은 트랜잭션 롤백으로 토큰이 유효한 채로 남는다 → 성공 200 + `Set-Cookie` + `{ member: { id, displayName } }`.
- 비밀번호는 verify의 응답·로그·오류(400/409 포함)에도 새지 않아야 한다.

**태스크별 수정**

| 태스크 | 변경 후 동작 / 해야 할 일 |
|--------|---------------------------|
| T004 (`schema.sql`) | `pending_signup`에서 `display_name`, `password_hash`, `consent_at`, `consent_version` 컬럼을 삭제한다. `member`는 그대로(비밀번호·동의 컬럼은 인증 때 채운다). 로컬 개발 DB는 새 컨테이너가 필요함을 승인 요약에 적는다 |
| T006 (`PendingSignup` record) | 위 컬럼 4개를 필드에서 제거 |
| T008 (`IntegrationTestBase`) | 시드 회원은 그대로. 신청 행을 직접 심는 헬퍼가 있으면 삭제된 컬럼을 채우지 않게 고친다. 인증 요청을 쉽게 보내는 헬퍼 `verify(token, displayName, password)`(동의 true, 고정 `consentVersion`)를 두면 아래 테스트에서 재사용된다(선택) |
| T012 (`SignupRequest`, `VerifyRequest`) | `SignupRequest`는 `email`(`@NotBlank @Email @Size(max=254)`)만. `VerifyRequest(String token, String displayName, String password, Boolean consent, String consentVersion)`: `token` `@NotBlank`, `displayName` `@NotBlank` + 앞뒤 공백 제거 후 2~20자(공백뿐이면 거부), `password` `@NotBlank @Size(min=8)` + UTF-8 72바이트 이하 검사, `consent` `@AssertTrue`, `consentVersion` `@NotBlank @Size(max=20)`. 필드 누락은 `null`을 거절한다(`displayName`은 더 이상 선택이 아님) |
| T013 (`SignupService.signup`) | 비밀번호 바이트 검사·BCrypt 해시·이름/동의 저장을 모두 제거한다. 순서: 이메일 정규화·도메인 검사(T030) → `email_key`가 `member`에 있으면 409 → 토큰 생성 → `pending_signup` INSERT(이메일·토큰 해시·만료·한도 컬럼만). 나머지(이벤트 발행, 응답, 중복 키 처리)는 그대로 |
| T014 (`SignupService.verify`) | `verify(token, displayName, password, consent, consentVersion)`로 바꾼다. 순서는 위 "새 동작 요약" ①~⑤. **본문 검증(②)이 `used_at` 선점(③)보다 앞**이어야 한다(검증 오류가 토큰을 소진하면 안 됨). 컨트롤러의 `@Valid`가 본문을 먼저 검증하면 토큰 조회(①)보다 400이 앞서게 되므로, 토큰 오류(400 `INVALID_TOKEN`/409/410)가 본문 오류보다 먼저 나가야 한다면 `@Valid`를 쓰지 말고 서비스에서 ① 뒤에 직접 검증해 `errors[]`를 모아 돌려준다(필드 누락 같은 형식 오류의 우선순위를 테스트로 고정). BCrypt 해시는 ② 통과 후 ④에서 만든다. `// ponytail:` 주석에 "가입 입력은 인증 링크를 누른 사람만 정한다(계정 선점 방지, 변경 B2)"를 남긴다 |
| T016 (`AuthController`) | 시그니처만 맞춘다(`signup` 본문 `SignupRequest`, `verify` 본문 `VerifyRequest`) |
| T030 | 도메인 검사는 이메일뿐이라 다른 입력 검증과 "모아서" 돌려주는 부분이 이메일 하나로 줄어든다 |
| T031 (재제출·`resend`) | 조건부 `UPDATE`에서 갱신하는 컬럼은 토큰·한도 컬럼뿐이고, 이름·비밀번호·동의 컬럼은 이제 존재하지 않는다. 시그니처·SQL은 사실상 그대로 |
| T010 (`SignupIT`) | **수정**: 시나리오 1(본문은 `{email}`만), 4, 5의 신청 쪽만 남긴다 — 형식 틀린 이메일, 이메일 필드 누락 → 400 `errors[email]`. **삭제**: 짧은 비밀번호·공백뿐인 이름·`consent=false`·`consentVersion` 누락·비밀번호 73바이트·표시 이름 경계(1·2·20·21자)·비밀번호 경계(7·8자, 72·73바이트)·`pending_signup.password_hash`가 `$2`로 시작하는지 검사. **추가**: 신청 본문에 `password`/`displayName` 같은 알 수 없는 필드를 보내도 무시되고 `pending_signup`에 저장되지 않는다(컬럼 자체가 없음을 `information_schema`로 확인) |
| T011 (`VerifyEmailIT`) | **수정**: 시나리오 2는 `{token, displayName, password, consent:true, consentVersion}`로 verify → 200, `member` 1행에 `password_hash`($2…)·`consent_at`·`consent_version` 기록. 시나리오 6은 "신청 후 인증 때 이미 쓰이는 이름(대소문자·공백 무시)으로 verify → 409 `DISPLAY_NAME_TAKEN`, `member` 늘지 않음, 같은 토큰에 새 이름으로 다시 → 200". 동시 N개 verify → 200 정확히 1건(각 요청에 올바른 본문). 마지막 줄 "같은 이름의 신청 두 건이 모두 202"는 신청에 이름이 없으므로 삭제하고 "서로 다른 두 이메일이 인증 때 같은 이름을 정하면 먼저 끝난 쪽만 200, 나머지 409 `DISPLAY_NAME_TAKEN`"으로 바꾼다. **추가**: (a) 본문 검증 오류 각각(이름 1자·21자·공백뿐, 비밀번호 7자·73바이트, `consent=false`, `consentVersion` 누락, 필드 전체 누락) → 400 `errors[필드]`이고 **`pending_signup.used_at`이 NULL인 채**이며 같은 토큰으로 고쳐 다시 보내면 200. (b) 이메일 충돌(verify 전에 같은 이메일이 `member`에 생김) → 409 `ALREADY_REGISTERED`, 롤백, `used_at` NULL. (c) 알 수 없는 토큰 + 잘못된 본문 → 400 `INVALID_TOKEN`(토큰 오류가 본문 오류보다 먼저). (d) 경계: 비밀번호 8자·72바이트 성공, 표시 이름 2·20자 성공 |
| T028 (`ResendIT`) | 시나리오 15를 "인증 전 같은 이메일로 다시 신청 → 202, 행 1개, 새 토큰, 옛 토큰으로 verify → 400 `INVALID_TOKEN`, 한도 이어서 셈"으로 바꾸고, "다른 이름·비밀번호를 다시 신청" 부분은 삭제. 시나리오 14·20·13은 그대로(단 verify 호출에 새 본문 필드를 채운다) |
| T017 등 가입 흐름을 쓰는 다른 IT (`LoginLogoutIT`, `PrivacyIT`, `SessionIT` 등) | signup+verify로 회원을 만드는 곳은 verify 본문을 새 형식으로 고치고, "인증 뒤 로그인은 처음 비밀번호로만 된다"는 "인증 때 정한 비밀번호로 로그인된다"로 바꾼다 |
| T036 (`PrivacyIT`) | 범위를 **verify 요청**으로 확대: 비밀번호 원문이 verify의 성공/400/409 응답 본문과 애플리케이션 로그(`OutputCaptureExtension`)에 없다. `pending_signup.password_hash` 검사는 삭제 |
| T038 (수동 검증) | quickstart 시나리오 1·2·5·6·15·21을 새 정의로 대조한다 |
| T037, T015, T021, T022~T026, T029, T032~T035 | 변경 없음 |

**기타**: plan.md·research.md·contracts·data-model·quickstart는 이미 B2로 고쳤다. 코드 변경 뒤 승인 요약에 "T004·T006·T012~T014·T031 및 위 테스트를 B2에 맞게 고쳤다"를 적고, 프론트(008) 변경 B2 블록과 계약이 맞는지 확인한다.

## Phase 1: Setup

**Purpose**: 의존성과 설정

- [ ] T001 `specs/002-login-signup/research.md`의 설명 체크 12문항(결정 1~12) 중 각 `(핵심 로직)` 태스크와 이어진 것(T009 세션: 결정 9·1, T013 신청: 결정 4·6·8, T014 인증: 결정 3·4, T021 로그인: 결정 2·7, T031 재발송: 결정 6, T034 잠금: 결정 7, T022 필터: 결정 10)은 그 태스크 승인 요약에서 답한다. 헌법 III에 따라 해당 코드 승인 전에 끝낸다.
- [ ] T002 `build.gradle`에 `spring-boot-starter-security`(이유: 비밀번호 해시·인증 필터·헤더 처리를 검증된 구현으로 쓰기 위해), `spring-boot-starter-mail`(이유: SMTP로 인증 메일을 보내는 표준 구현), 테스트용 `spring-boot-starter-security-test`(Boot 4 관례 이름, 구현 때 확인됨 2026-10-27)를 추가한다. **Security를 넣는 순간 모든 엔드포인트가 막히므로** 같은 태스크에서 임시 `main/auth/SecurityConfig.java`를 두어 `anyRequest().permitAll()`, CSRF·폼 로그인·HTTP Basic 비활성화, 상태 없는 세션으로 두고 `// 임시: US2(T022)에서 교체`라고 적는다. `./gradlew test`가 001 그대로 통과해야 한다.
- [ ] T003 [P] `main/auth/AuthProperties.java`(`@ConfigurationProperties("camgong.auth")` record)와 `res/application.yml`: contract의 설정 표 그대로 — `allowed-email-domains`(환경변수 `ALLOWED_EMAIL_DOMAINS`, 개발 기본값 `school.ac.kr`, 운영은 사용자가 준 값. 기본값은 코드에 두되 운영 값은 저장소에 넣지 않는다), `session-ttl=14d`, `session-touch-interval=10m`, `verify-token-ttl=24h`, `mail.resend-interval=1m`, `mail.daily-limit=5`, `login.max-failures=5`, `login.lock-duration=15m`, `cookie-secure=false`(운영 `true`), `bcrypt-cost=10`, `verify-link-base-url`(인증 메일 링크의 앞부분, 개발 기본값 `http://localhost:5173/verify?token=`). `camgong.mail.sender=logging|smtp`(기본 `logging`)와 `camgong.mail.*` SMTP 접속 정보는 환경변수로만 받는다. `main/auth/AuthConfig.java`: `PasswordEncoder` 빈(`new BCryptPasswordEncoder(cost)`)과 `@EnableAsync`

---

## Phase 2: Foundational

**Purpose**: 모든 스토리가 쓰는 스키마·회원·세션·메일 이음새·테스트 기반

**⚠️ 이 단계가 끝나기 전에는 스토리 작업을 시작하지 않는다**

- [ ] T004 `res/schema.sql` 수정(모든 인덱스·유일 제약은 `CREATE TABLE IF NOT EXISTS` 정의 안에 넣는다. 재실행에 안전해야 하며 `SchemaIT`가 통과해야 한다). **로컬 개발 DB는 `CREATE TABLE IF NOT EXISTS`로 고쳐지지 않으므로 컨테이너를 새로 만들어야 한다**(테스트는 매번 새 컨테이너라 영향 없음. 승인 요약에 적는다):
  - `member`: `id BIGINT AUTO_INCREMENT PRIMARY KEY`(테스트 시드가 id를 직접 넣어도 된다), `name VARCHAR(20) NOT NULL`, `display_name_key VARCHAR(20) NOT NULL UNIQUE`, `email VARCHAR(254) NOT NULL`, `email_key VARCHAR(254) NOT NULL UNIQUE`, `password_hash VARCHAR(100) NOT NULL`, `consent_at DATETIME(6) NOT NULL`, `consent_version VARCHAR(20) NOT NULL`, `created_at DATETIME(6) NOT NULL`
  - `pending_signup`: `id BIGINT AUTO_INCREMENT PRIMARY KEY`, `email_key VARCHAR(254) NOT NULL UNIQUE`, `email VARCHAR(254) NOT NULL`, `display_name VARCHAR(20) NOT NULL`(유일 제약 없음), `password_hash VARCHAR(100) NOT NULL`, `consent_at DATETIME(6) NOT NULL`, `consent_version VARCHAR(20) NOT NULL`, `token_hash CHAR(64) NOT NULL`(`KEY (token_hash)`), `token_expires_at DATETIME(6) NOT NULL`, `last_sent_at DATETIME(6) NOT NULL`, `window_started_at DATETIME(6) NOT NULL`, `sent_count INT NOT NULL`, `used_at DATETIME(6) NULL`, `created_at DATETIME(6) NOT NULL`
  - `login_session`: `id BIGINT AUTO_INCREMENT PRIMARY KEY`, `token_hash CHAR(64) NOT NULL UNIQUE`, `member_id BIGINT NOT NULL`(`KEY (member_id)`, FK `member`), `created_at DATETIME(6) NOT NULL`, `last_used_at DATETIME(6) NOT NULL`(`KEY (last_used_at)`)
  - `login_attempt`: `email_key VARCHAR(254) PRIMARY KEY`, `failed_count INT NOT NULL`, `locked_until DATETIME(6) NULL`, `updated_at DATETIME(6) NOT NULL`
- [ ] T005 `main/member/Member.java`, `main/member/MemberRepository.java`, `main/auth/Keys.java`: `Member`에 T004의 컬럼을 매핑(`id`는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`, 시각은 `Instant`, 서비스가 `Instant.now(clock)`으로 설정). 기존 `getName()` 이름은 유지한다. `MemberRepository`에 `existsByEmailKey`, `findByEmailKey`, `existsByDisplayNameKey`. `Keys.normalize(String)` = trim + `toLowerCase(Locale.ROOT)`(이메일 키·표시 이름 키 공용, 단위 테스트 포함: 앞뒤 공백, 대소문자, 한글은 그대로)
- [ ] T006 [P] `main/auth/PendingSignup.java`, `main/auth/LoginSession.java`, `main/auth/LoginAttempt.java`: JPA 엔티티가 아니라 `JdbcClient` 결과를 담는 record(조건부 `UPDATE`가 핵심이라 영속성 컨텍스트에 두지 않는다). 컬럼은 T004와 같다. 주석: `// ponytail: 인증 테이블은 JdbcClient 전용, 영속 엔티티 아님`
- [ ] T007 [P] `main/auth/MailSender.java`(인터페이스: `void sendVerification(String toEmail, String verifyLink)`), `main/auth/LoggingMailSender.java`(개발·테스트용: 받는 사람만 INFO로 남기고 링크는 DEBUG로만 남긴다. 최근 발송 목록을 메모리에 쌓아 테스트가 `sent()`로 읽고 `clear()`할 수 있게 한다), `main/auth/SmtpMailSender.java`(`JavaMailSender` 사용, `camgong.mail.sender=smtp`일 때만 등록 `@ConditionalOnProperty`). 제목·본문은 한국어, 본문은 링크와 "24시간 안에 확인" 안내만. 주석: `// ponytail: 발송 실패는 로그만 남기고 사용자가 재발송으로 복구`
- [ ] T008 `test/support/IntegrationTestBase.java` 수정: (1) 시드 회원 1·2·3에 `email`(`m1@school.ac.kr` …), `email_key`, `display_name_key`, 더미 `password_hash`, `consent_at`, `consent_version`, `created_at`을 채운다. 정리 대상 테이블에 `login_session`, `pending_signup`, `login_attempt`를 `participation` 앞에 추가(FK 순서에 맞게). (2) `camgong.auth.bcrypt-cost=4`로 테스트 속도 확보. (3) `LoggingMailSender`를 쓰고 각 테스트 전에 `clear()`. (4) `as(memberId)` 안은 **아직 헤더 방식 그대로** 둔다(US2 T024에서 바꾼다). 기존 001 테스트가 모두 통과해야 한다
- [ ] T009 `main/auth/SessionService.java` **(핵심 로직)**: `issue(memberId)` — `SecureRandom` 32바이트를 URL-safe Base64로 만든 원문을 반환하고, DB에는 SHA-256 hex만 `login_session`에 저장(`created_at`·`last_used_at`은 `Instant.now(clock)`). `resolve(rawToken)` — `token_hash`로 조회해 `last_used_at + session-ttl > now`일 때만 회원 id를 돌려주고, 만료면 행을 지우고 `empty`. `last_used_at < now − session-touch-interval`일 때만 `UPDATE login_session SET last_used_at = :now WHERE id = :id AND last_used_at < :threshold`(읽기 요청마다 쓰기가 생기지 않게). `revoke(rawToken)` — 해당 행 삭제(멱등). 쿠키 생성 도우미 `cookie(rawToken)`: 이름 `camgong_session`, `HttpOnly`, `SameSite=Lax`, `Path=/`, `Secure`는 `cookie-secure` 설정, `Max-Age = session-ttl`. 삭제용 쿠키 도우미도 포함. 시간 판정은 모두 주입한 `Clock`. 단위가 아니라 통합 테스트(T019)로 검증한다

**Checkpoint**: `./gradlew test`가 001 그대로 통과하고 `SchemaIT`가 새 스키마로 통과한다.

---

## Phase 3: User Story 1 - 학교 이메일로 가입하고 인증해서 쓰기 (Priority: P1) 🎯

**Goal**: 가입을 신청하면 인증 메일이 나가고, 링크의 토큰으로 확인하면 회원이 만들어지고 로그인 쿠키를 받는다.

**Independent Test**: `POST /api/auth/signup` → `LoggingMailSender`가 기록한 토큰으로 `POST /api/auth/verify` → 200 + `camgong_session` 쿠키, `member` 1행, `login_session` 1행.

### Tests for User Story 1

- [ ] T010 [P] [US1] `test/auth/SignupIT.java`: quickstart 시나리오 1(202, 본문 `email`·`resendAvailableAt`, 메일 1통 기록, `pending_signup` 1행, `member`에는 아직 없음, 아직 로그인 불가), 4(이미 인증된 이메일 → 409 `ALREADY_REGISTERED`, 대소문자·앞뒤 공백이 달라도 같은 이메일), 5(짧은 비밀번호·형식 틀린 이메일·공백뿐인 이름·`consent=false`·`consentVersion` 누락 → 400과 필드별 `errors`, 비밀번호 73바이트(한글 25자) → 400 `password`). 경계: 비밀번호 7·8자, 72·73바이트, 표시 이름 1·2·20·21자. 실패한 신청이 `pending_signup`에 행을 남기지 않는지, 비밀번호 원문이 `pending_signup`에 없는지(`password_hash`가 `$2`로 시작)
- [ ] T011 [P] [US1] `test/auth/VerifyEmailIT.java`: 시나리오 2(기록된 토큰으로 verify → 200, `member.id`·`displayName` 응답, 쿠키 속성 `HttpOnly`·`SameSite=Lax`, `member` 1행에 `email_key`·`display_name_key`·`consent_*`, `login_session` 1행, 같은 토큰을 다시 → 409 `TOKEN_USED`), 6(같은 이름(대소문자·공백 무시)을 쓰는 회원이 이미 있으면 verify가 409 `DISPLAY_NAME_TAKEN`이고 `member`는 늘지 않으며, 같은 토큰에 `displayName`을 바꿔 다시 보내면 가입 완료), 14의 일부(25시간 뒤 토큰 → 410 `TOKEN_EXPIRED`, 알 수 없는 토큰 → 400 `INVALID_TOKEN`). 같은 토큰으로 동시에 N개 verify → 200 정확히 1건, 나머지 409 `TOKEN_USED`, `member` 1행. 인증 전에는 이름이 점유되지 않는다(같은 이름의 신청 두 건이 모두 202)

### Implementation for User Story 1

- [ ] T012 [P] [US1] `main/auth/SignupRequest.java`(`email` `@NotBlank @Email @Size(max=254)`, `displayName` `@NotBlank @Size(min=2, max=20)`(앞뒤 공백 제거 후 길이, 공백뿐이면 거부), `password` `@NotBlank @Size(min=8)`, `consent` `@AssertTrue`, `consentVersion` `@NotBlank @Size(max=20)`), `SignupResponse(String email, OffsetDateTime resendAvailableAt)`, `VerifyRequest(String token, String displayName)`(`token` `@NotBlank`, `displayName`은 선택이고 있으면 2~20자), `MemberView(Long id, String displayName)`, `AuthResponse(MemberView member)`. 오프셋은 001 응답과 같은 KST 고정
- [ ] T013 [US1] `main/auth/SignupService.java`의 `signup` **(핵심 로직)**: `@Transactional`. 비밀번호 UTF-8 바이트가 72를 넘으면 `errors[password]`로 400(길이 검사는 글자 수가 아니라 바이트) → `email_key`가 `member`에 있으면 409 `ALREADY_REGISTERED` → BCrypt 해시 → 무작위 32바이트 토큰(URL-safe Base64)을 만들고 SHA-256 hex를 `token_hash`로, `token_expires_at = now + verify-token-ttl`, `last_sent_at = window_started_at = now`, `sent_count = 1`로 `pending_signup` INSERT. `email_key` 중복 키(이미 신청이 있음)면 이 스토리에서는 `429 TOO_MANY_REQUESTS`로 거부한다(`retryAt = last_sent_at + 1분`, 재제출 갱신은 T031). 커밋 뒤 메일을 보낼 `VerificationMailRequested(email, token)` 이벤트를 발행한다. 응답 `resendAvailableAt = now + resend-interval`. 이름은 이 시점에 중복 검사하지 않는다(FR-003, FR-006)
- [ ] T014 [US1] `main/auth/SignupService.java`의 `verify` **(핵심 로직)**: `@Transactional`. 토큰의 SHA-256으로 `pending_signup` 조회 → 없으면 400 `INVALID_TOKEN` → `used_at`이 있으면 409 `TOKEN_USED` → `token_expires_at <= now`면 410 `TOKEN_EXPIRED` → `UPDATE pending_signup SET used_at = :now WHERE id = :id AND used_at IS NULL`을 **먼저** 실행해 영향 행 0이면 409 `TOKEN_USED`(동시 verify 중 하나만 통과, `useAffectedRows=true` 전제) → 이름 = 요청의 `displayName`이 있으면 그것, 없으면 신청의 `display_name` → `display_name_key`·`email_key`로 `member` INSERT. 이름이나 이메일 `UNIQUE` 위반(`DuplicateKeyException`)이면 예외를 던져 같은 트랜잭션이 롤백되게 한다(`used_at`도 되돌아가 토큰이 유효한 채로 남는다) → 이름 충돌은 409 `DISPLAY_NAME_TAKEN`, 이메일 충돌은 409 `ALREADY_REGISTERED`. 성공하면 `SessionService.issue(memberId)`로 세션을 만들고 `AuthResponse`와 쿠키를 반환한다. 주석: `// ponytail: 인증 링크는 화면이 POST로 확인(메일 스캐너의 GET 미리 열기 방지)`
- [ ] T015 [US1] `main/auth/VerificationMailListener.java`: `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`로 `MailSender.sendVerification(email, verify-link-base-url + token)`. 발송 예외는 잡아서 로그에만 남기고(토큰·링크는 로그에 쓰지 않는다) 사용자 요청에는 영향이 없다. 롤백된 신청은 메일이 나가지 않는다
- [ ] T016 [US1] `main/auth/AuthController.java`: `POST /api/auth/signup`(202 `SignupResponse`), `POST /api/auth/verify`(200 `AuthResponse` + `Set-Cookie`). `@Valid` 본문. 이 두 경로는 인증 없이 접근 가능해야 한다(임시 permitAll 상태). 오류는 001의 `ApiException`/핸들러로, `title`이 `ALREADY_REGISTERED`·`DISPLAY_NAME_TAKEN`·`TOKEN_USED` 등 계약의 코드. 410·429는 상태 코드로 지정

**Checkpoint**: T010·T011이 통과한다. 이 시점의 `GET /api/auth/me`는 아직 없다.

---

## Phase 4: User Story 2 - 로그인하고 하던 일로 돌아가기, 로그아웃하기 (Priority: P1)

**Goal**: 쿠키 세션으로 본인이 식별되고, 보호된 엔드포인트는 비로그인에 401이며, `X-Member-Id`는 더 이상 인증하지 않는다.

**Independent Test**: 가입한 회원이 로그인해 쿠키로 보호된 엔드포인트를 쓰고, 로그아웃 뒤 같은 쿠키는 401이며, `X-Member-Id: 1`만 붙인 요청은 401이다.

### Tests for User Story 2

- [ ] T017 [P] [US2] `test/auth/LoginLogoutIT.java`: 시나리오 8(올바른 정보 → 200 + 쿠키, `GET /api/auth/me` 200 `{member:{id,displayName}}`), 9(로그아웃 204, 같은 쿠키로 `/me` → 401 `UNAUTHENTICATED`, 로그인하지 않은 로그아웃도 204), 10(틀린 비밀번호와 없는 이메일이 **상태 코드·`title`·`detail`이 완전히 같은** 401 `INVALID_CREDENTIALS`), 11(신청만 하고 인증하지 않은 이메일 로그인 → 같은 401), 18(두 기기 로그인 후 한쪽 로그아웃, 다른 쪽 유지, `login_session` 행 수). 이메일은 대소문자·앞뒤 공백을 무시한다
- [ ] T018 [P] [US2] `test/auth/AuthGuardIT.java`: 시나리오 7·19 — 현재 있는 보호 엔드포인트 전부(공구 상세·개설·수정·취소)를 헤더·쿠키 없이 호출하면 401 `UNAUTHENTICATED`(본문은 `ProblemDetail`), 쿠키 없이 `X-Member-Id: 1`만 붙여도 401, **올바른 쿠키와 함께 다른 회원의 `X-Member-Id`를 붙여도 쿠키의 회원으로 처리**된다. `POST /api/auth/signup`·`login`·`verify`·`logout`은 비로그인으로 접근 가능. 이후 기능이 추가될 때 보호 목록을 한 곳(배열)에서 늘릴 수 있게 구조를 잡는다
- [ ] T019 [P] [US2] `test/auth/SessionIT.java`: 시나리오 17 — `MutableClock`으로 13일 뒤 사용하면 유지되고 `last_used_at`이 갱신되며(슬라이딩), 그 시점에서 다시 13일 뒤도 유지, 마지막 사용 후 15일째는 401이며 만료 행이 지워진다. `last_used_at`이 10분 안에는 갱신되지 않고(UPDATE 수를 `StatementInspector` 또는 컬럼 값으로 확인) 10분이 지나면 갱신된다. 쿠키에 원문이 있고 DB에는 SHA-256 hex만 있다
- [ ] T020 [P] [US2] `test/auth/OriginCheckIT.java`: contract의 CSRF 절 — 쿠키를 가진 요청이 `Origin: https://evil.example`로 `POST`/`PUT`하면 403, 같은 출처 `Origin`이면 통과, `Origin` 없이 `Referer`만 다른 출처면 403, **`Origin`·`Referer`가 모두 없으면 통과**(tasks 표 c), `GET`은 검사하지 않는다, `Content-Type`이 `application/json`이 아니면 거부(415)

### Implementation for User Story 2

- [ ] T021 [US2] `main/auth/LoginService.java`의 `login` **(핵심 로직)**: 이메일을 `Keys.normalize` → `member` 조회. 없으면 **미리 만들어 둔 더미 BCrypt 해시와 비교**해 응답 시간 차이를 줄이고 401 `INVALID_CREDENTIALS`(인증 전 신청만 있는 이메일도 같은 경로, tasks 표 b). 있으면 `PasswordEncoder.matches`, 틀리면 같은 401. 맞으면 `SessionService.issue`로 쿠키 발급, `AuthResponse` 반환. 비밀번호·이메일은 로그에 쓰지 않는다. 잠금은 US4(T035)에서 이 메서드 앞뒤에 끼운다. 주석: `// ponytail: 계정 존재 여부를 응답·시간으로 드러내지 않는다`
- [ ] T022 [US2] `main/auth/AuthFilter.java`와 `main/auth/SecurityConfig.java` **(핵심 로직)**: 필터는 쿠키 `camgong_session` → `SessionService.resolve` → 회원 id·이름을 `SecurityContext`에 넣는다. `X-Member-Id`는 읽지 않는다(FR-014). `SecurityConfig`는 T002의 임시 설정을 교체한다: `permitAll`은 `POST /api/auth/signup`, `/signup/resend`, `/verify`, `/login`, `/logout`(그 외 `/api/auth/me`는 인증 필요), 공구 목록(`GET /api/groupbuys`, 003이 구현하면 열린다)이고 그 밖의 `/api/**`는 인증 필요. 인증 실패는 401 `ProblemDetail`(`title: UNAUTHENTICATED`, `errors: []`)로 직접 응답하는 `AuthenticationEntryPoint`. CSRF·폼 로그인·HTTP Basic은 끄고(CSRF는 T025의 Origin 검사로 대신한다) 상태 없는 세션 정책을 쓴다
- [ ] T023 [US2] `main/auth/CurrentMember.java`(`record CurrentMember(Long id, String displayName)`)와 `HandlerMethodArgumentResolver` 등록(`main/auth/AuthWebConfig.java`): 컨트롤러 인자에서 `SecurityContext`의 회원을 꺼낸다. `main/groupbuy/GroupBuyController.java`의 `@RequestHeader("X-Member-Id")`를 모두 `CurrentMember`로 바꾸고, 서비스가 회원 존재를 확인하던 401 분기는 필터가 보장하므로 정리한다(동작은 그대로 401). `grep -rn "X-Member-Id" src/main`이 비어야 한다
- [ ] T024 [US2] `test/support/IntegrationTestBase.java`의 `as(memberId)`를 **쿠키 방식**으로 바꾼다: 회원의 `login_session` 행을 직접 삽입하고(원문 토큰은 테스트가 알도록 고정 규칙으로 생성) 요청에 `Cookie: camgong_session=<원문>`을 붙인다. 시그니처는 그대로여서 001 테스트 본문은 고치지 않는다(`grep -rn "X-Member-Id" src/test`에는 `AuthGuardIT`의 위조 시도만 남는다). 001 테스트가 모두 통과해야 한다
- [ ] T025 [US2] `main/auth/OriginCheckFilter.java`: 상태를 바꾸는 메서드(`POST`·`PUT`·`PATCH`·`DELETE`)에서 `Origin`(없으면 `Referer`의 출처)이 요청의 호스트와 같은 출처이거나 설정 `camgong.auth.allowed-origins`(쉼표 목록, 기본 비어 있음)에 있어야 한다. 다르면 403 `ProblemDetail`(`title: FORBIDDEN_ORIGIN`). 둘 다 없으면 통과. 프록시 뒤 호스트 문제를 피하려면 `allowed-origins`를 쓴다는 주석을 남긴다
- [ ] T026 [US2] `main/auth/AuthController.java`에 `POST /api/auth/login`(200 `AuthResponse` + 쿠키), `POST /api/auth/logout`(204, 쿠키 삭제, 세션 즉시 폐기, 멱등), `GET /api/auth/me`(200 `AuthResponse`, 비로그인은 필터가 401)

**Checkpoint**: T017~T020이 통과하고, 001의 모든 테스트가 쿠키 방식으로 그대로 통과한다.

---

## Phase 5: User Story 3 - 학교 이메일이 아닌 사람 걸러내기, 인증 메일 문제 풀기 (Priority: P2)

**Goal**: 허용 도메인이 아닌 이메일을 거부하고, 인증 메일을 한도 안에서 다시 받으며, 인증 전 재제출은 (신청에 이메일뿐이므로) 토큰·한도만 갱신해 새 토큰을 받는다.

**Independent Test**: `student@gmail.com` 신청이 400이고, 1분 안의 재발송이 429, 1분 뒤 재발송이 202이며 이전 링크가 무효가 된다.

### Tests for User Story 3

- [ ] T027 [P] [US3] `test/auth/SignupDomainIT.java`: 시나리오 12 — `student@gmail.com`, `student@evil.school.ac.kr.attacker.com`, `student@school.ac.kr.evil.com`, `student@sub.school.ac.kr`(목록에 없는 하위 도메인), `a@b@school.ac.kr`은 400 `errors[email]`이고 `pending_signup`에 행이 없다. `Student@SCHOOL.AC.KR`·앞뒤 공백은 통과한다
- [ ] T028 [P] [US3] `test/auth/ResendIT.java`: 시나리오 13(신청 직후 재발송 → 429 `TOO_MANY_REQUESTS`와 `Retry-After` 헤더·본문 `retryAt`, `MutableClock` 61초 뒤 → 202 + 새 메일, 24시간 창에서 6번째 → 429 `retryAt`이 창이 끝나는 시각, 창이 지나면 다시 1번부터), 14의 나머지(재발송 전 이전 토큰으로 verify → 400 `INVALID_TOKEN`, 새 토큰은 성공), 15(인증 전에 같은 이메일로 다시 신청 → 202, `pending_signup`은 여전히 1행이고 새 토큰·`last_sent_at`·`sent_count`만 바뀜, 옛 토큰은 `INVALID_TOKEN`, 한도는 이어서 센다. 신청 본문에 이름·비밀번호가 없다: 변경 B2), 20(같은 신청을 동시에 N개 → 상태가 202 정확히 1건이고 나머지 429, `pending_signup` 1행, 메일 1통), 재발송 동시 N개 → 202 정확히 1건. 이미 인증된 이메일·신청 내역이 없는 이메일의 재발송은 **똑같은 404**

### Implementation for User Story 3

- [ ] T029 [US3] `main/common/ApiException.java`, `ApiExceptionHandler.java`에 추가 속성·헤더 지원: `ApiException`이 `Map<String,Object> properties`와 `Retry-After`(초)를 가질 수 있게 하고(기존 `reason` 처리와 같은 방식), 핸들러가 `ProblemDetail.setProperty`와 응답 헤더로 내보낸다. `retryAt`은 오프셋 포함 ISO-8601. 기존 001 테스트가 그대로 통과해야 한다
- [ ] T030 [US3] `SignupService.signup`에 허용 도메인 검사 추가: 이메일을 `Keys.normalize`한 뒤 마지막 `@` 뒤의 도메인이 `camgong.auth.allowed-email-domains` 목록과 **정확히 일치**해야 한다(`endsWith`·`contains` 금지. `@`가 둘 이상이면 거부). 위반은 `errors[email]` 400. 다른 입력 검증과 함께 모아서 한 번에 돌려준다. 이 검사는 BCrypt 해시보다 먼저 한다(거부된 요청이 비싼 연산을 쓰지 않게)
- [ ] T031 [US3] `main/auth/SignupService.java`에 재제출 갱신과 `resend` **(핵심 로직)**: T013의 중복 키 분기를 data-model의 조건부 `UPDATE` 한 문장으로 바꾼다 — `UPDATE pending_signup SET sent_count = IF(window_started_at < :now − INTERVAL 24 HOUR, 1, sent_count + 1), window_started_at = IF(window_started_at < :now − INTERVAL 24 HOUR, :now, window_started_at), last_sent_at = :now, token_hash = :hash, token_expires_at = :now + INTERVAL 24 HOUR (신청에는 이메일뿐이라 갱신할 이름·비밀번호·동의가 없다: 변경 B2) WHERE email_key = :k AND last_sent_at <= :now − INTERVAL 1 MINUTE AND (window_started_at < :now − INTERVAL 24 HOUR OR sent_count < 5)`. **`SET`절의 평가 순서(왼쪽에서 오른쪽, spikes #10)를 테스트로 확인**하고 어긋나면 승인 요약에 알린다. 영향 행 0이면 `SELECT`로 이유를 구분(1분 간격이면 `retryAt = last_sent_at + 1분`, 일일 한도면 `window_started_at + 24시간`)해 429. `now`는 DB `NOW()`가 아니라 `Clock` 파라미터. 재제출이 성공하면 이전 토큰은 `token_hash` 교체로 무효가 된다. `resend(email)`와 재제출은 같은 조건부 `UPDATE`(이메일 외 갱신할 값 없음, 변경 B2)로 새 토큰을 만들고 이벤트 발행. 신청이 없거나 이미 `member`에 있거나 `used_at`이 있는 이메일은 같은 404. 영향 행 판정은 JDBC `useAffectedRows=true`를 전제한다. 주석: `-- ponytail: MySQL 전용 문법`
- [ ] T032 [US3] `main/auth/AuthController.java`에 `POST /api/auth/signup/resend`(본문 `{ "email": "..." }`, 이메일 형식 400, 성공 202 `SignupResponse`)

**Checkpoint**: T027·T028이 통과하고 시나리오 20(더블 제출)이 동시성 테스트에서 안정적이다.

---

## Phase 6: User Story 4 - 로그인 남용 막기 (Priority: P3)

**Goal**: 같은 이메일로 연속 5번 틀리면 15분간 로그인이 거부되고, 없는 이메일도 똑같이 센다.

**Independent Test**: 같은 이메일로 5번 틀린 뒤 올바른 정보로 로그인해도 429 `LOGIN_LOCKED`이고, 15분 뒤 성공한다.

- [ ] T033 [P] [US4] `test/auth/LoginThrottleIT.java`: 시나리오 16 — 같은 이메일로 5번 실패 후 6번째(올바른 정보)가 429 `LOGIN_LOCKED`(`Retry-After`·`retryAt`), `MutableClock`을 15분 뒤로 하면 성공, 4번 실패 뒤 성공하면 횟수가 0부터 다시, 성공 뒤 다시 틀리면 1부터. **가입되지 않은 이메일도 5번째에 똑같이 잠기고 응답의 상태·`title`·본문 구조가 존재하는 계정과 같다**. 잠금 중의 시도는 실패 횟수를 늘리지 않고 잠금 시각을 연장하지 않는다. 같은 이메일로 동시에 10번 틀린 로그인 → 정확히 한 번 잠기고 `failed_count`가 손실 없이 센다. 이메일 대소문자·공백이 달라도 같은 키
- [ ] T034 [US4] `main/auth/LoginService.java`에 실패 기록 `recordFailure` **(핵심 로직)**: data-model의 `INSERT INTO login_attempt … ON DUPLICATE KEY UPDATE`를 `JdbcClient`로 실행 — `failed_count = IF(locked_until IS NOT NULL AND locked_until <= :now, 1, failed_count + 1)`, `locked_until = IF(failed_count >= max-failures, :now + lock-duration, locked_until)`, `updated_at = :now`. **`ON DUPLICATE KEY UPDATE`의 별칭 문법과 평가 순서(`failed_count`가 갱신된 값을 보는지)는 구현 때 테스트로 검증**하고 결과를 승인 요약에 적는다. `now`는 `Clock` 파라미터. 성공 시 `login_attempt` 행 삭제(횟수 초기화). 주석: `-- ponytail: 이메일 키로 센다. 남이 일부러 실패를 쌓아 15분 막을 수 있다(수용), 필요하면 IP 단위 추가`
- [ ] T035 [US4] `LoginService.login`에 잠금을 끼운다: 이메일 정규화 직후 `login_attempt`를 조회해 `locked_until > now`면 비밀번호 검사 없이 429 `LOGIN_LOCKED`(`retryAt = locked_until`). 비밀번호가 틀리면(없는 계정 포함) `recordFailure`. 그 5번째 실패가 곧바로 잠금을 건다

**Checkpoint**: T033이 통과하고 T017의 로그인 실패 안내가 여전히 통과한다.

---

## Phase 7: Polish & Cross-Cutting

- [ ] T036 [P] `test/auth/PrivacyIT.java`: 시나리오 21 — 가입·인증·로그인·`/me`·공구 상세(개설자·참여자 시점)·오류 응답의 JSON 전체에 `email`·`password`·`passwordHash`·`@school.ac.kr`이 나타나지 않는다(`AuthResponse`의 `member`는 `id`·`displayName`만). 테스트 중 수집한 애플리케이션 로그에 비밀번호 원문과 인증 토큰이 없다(`OutputCaptureExtension` 등). DB에 비밀번호 원문이 없다(`member.password_hash`가 BCrypt 형식. `pending_signup`에는 비밀번호 컬럼이 없다: 변경 B2). **범위가 `verify` 요청으로 확대**: 비밀번호 원문이 verify의 응답·오류 본문(검증 400, 409 포함)·로그에 없다
- [ ] T037 `main/auth/SessionCleanup.java`: `@Scheduled(fixedDelay = 1h)`로 (1) `login_session` 중 `last_used_at < now − session-ttl` 삭제, (2) `login_attempt` 중 `updated_at < now − 1일`이고 잠금이 풀린 행 삭제, (3) `pending_signup` 중 `token_expires_at < now − 7일`이거나 `used_at < now − 1일`인 행 삭제. 테스트 프로파일에서는 자동 실행을 끄고(`camgong.auth.cleanup.enabled=false`) 메서드를 직접 호출해 `MutableClock`으로 검증하는 `test/auth/SessionCleanupIT.java`도 추가. 주석: `// ponytail: 단일 인스턴스 전제, 다중 인스턴스면 락 필요`
- [ ] T038 `./gradlew test` 전체 통과 확인 후 quickstart.md 시나리오 1~22를 수동(`bootRun` + curl/HTTP 파일)으로도 한 번 실행해 기대 결과와 대조한다. **시나리오 22**: 로그인 100회의 p95가 1초 이내인지 `bcrypt-cost`별(10, 12)로 재서 승인 요약에 **측정값**을 적는다(측정하지 못하면 "확인 못 함"). 실제 SMTP로 한 번 보내 보는 것은 사용자의 계정이 필요하므로 계정이 없으면 "확인 못 함"으로 적는다
- [ ] T039 [P] `ponytail:` 주석이 남은 한계(인증 테이블 JdbcClient 전용, MySQL 전용 한도 쿼리, 발송 실패는 로그만, 정리 작업 단일 인스턴스, 이메일 키 잠금의 계정 잠금 악용, `schema.sql`→Flyway, 도메인 목록 설정값)를 모두 담았는지 확인하고, `grep -rn "X-Member-Id" src/main`이 비어 있는지, `grep -rn "X-Member-Id" src/test`에는 위조 시도 테스트만 있는지 확인한다. 이 기능이 끝나면 `docs/architecture.md` 6.5의 "로그인 전 공개 배포 금지" 제한이 풀리므로 관제탑에 알린다(세션은 문서를 고치지 않는다)
- [ ] T040 [P] `specs/002-login-signup/plan.md`의 소스 트리를 실제 파일 구성과 대조해 달라진 점을 승인 요약에 적는다(문서는 관제탑이 고친다)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001은 언제든. T002 → T003 순서(T003의 `PasswordEncoder`는 security 의존성이 필요)
- **Foundational (Phase 2)**: Setup 후. T004 → T005 → T006·T007 병렬 → T008 → T009. 모든 스토리를 막는다
- **US1 → US2 → US3 → US4** 순서로 진행한다. US2는 US1이 만든 `AuthController`·`SignupService`를 확장하고, US3는 US1의 `signup`을 고치며, US4는 US2의 `LoginService`를 고치므로 병렬로 진행하지 않는다
- **Polish**: 원하는 스토리가 끝난 뒤. T036은 US2 이후면 가능

### 태스크 간 의존

- T002의 임시 `SecurityConfig`는 T022에서 교체된다. 둘 사이에 보호가 없는 상태이므로 **공개 배포 금지**
- T013 → T014 → T015(이벤트 발행·수신) → T016
- T022(필터)는 T009(세션)와 T023(`CurrentMember`) 이전에 완성할 수 없다. T024(`as()` 쿠키 전환)는 T022 이후
- T031은 T013·T029 이후. T035는 T021·T034 이후
- 이후 기능(003~007)은 `CurrentMember`로 요청자를 받고, 테스트는 `as(memberId)`를 쓴다. 003이 `GET /api/groupbuys` 목록을 구현할 때 T022의 `permitAll`과 `AuthGuardIT`의 공개 목록을 맞춘다

## Parallel Example

```bash
# Foundational 중 서로 다른 파일
T006 레코드 / T007 메일 이음새

# US1 테스트 두 개
T010 SignupIT / T011 VerifyEmailIT

# US2 테스트 네 개
T017 LoginLogoutIT / T018 AuthGuardIT / T019 SessionIT / T020 OriginCheckIT
```

## Implementation Strategy

- **MVP**: Phase 1~4(US1+US2). 이 시점에 임시 헤더가 사라지고 로그인이 동작한다
- **줄이는 순서**: 일정이 막히면 US4(잠금) → US3의 재발송 한도 → US3의 도메인 검사 순서로 줄인다. 단 **도메인 검사(T030)는 서비스 신뢰의 전제**라 마지막까지 남긴다
- **사용자 준비물**: 운영 SMTP 계정, 허용 학교 이메일 도메인 값(`ALLOWED_EMAIL_DOMAINS`). 개발·테스트는 없어도 진행된다
