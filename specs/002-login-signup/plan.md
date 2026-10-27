# Implementation Plan: 회원 가입·로그인·로그아웃

**Branch**: `002-login-signup` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-login-signup/spec.md`

> **추천안으로 확정 (2026-10-26)**: 이 plan의 설계 결정은 모두 [research.md](research.md)의 **추천안 기준**으로 쓴 가안이다. 2026-10-26 사용자가 추천대로 확정했다(준비물·오류 코드 이름 등 남은 항목은 decisions-pending.md). 이 기능은 새 의존성(메일, 암호화, 보안 프레임워크)과 운영 준비물(메일 계정, 학교 도메인)이 걸려 있어 결정이 가장 많다. 결정 목록은 [docs/overnight/decisions-pending.md](../../docs/overnight/decisions-pending.md).

## Summary

학교 이메일만 넣어 가입을 신청하고, 인증 메일의 링크를 연 사람이 인증 화면에서 표시 이름·비밀번호·동의를 정해 가입을 마치며(변경 B2, 계정 선점 방지), 이메일과 비밀번호로 로그인한다. 임시 `X-Member-Id` 헤더를 실제 로그인으로 바꾼다.

추천안의 골자:

- 로그인 상태는 **서버가 저장하는 세션**(DB)과 `HttpOnly` 쿠키다. 로그아웃 즉시 무효, 14일 슬라이딩, 다기기를 서버가 직접 통제할 수 있어야 해서 상태 없는 토큰보다 맞다.
- 인증 전 가입 신청(이메일·토큰 해시·한도)은 **`pending_signup` 테이블**에 따로 둔다. `member`에는 인증을 마친 회원만 있어서 이메일·이름 유일성 제약이 단순하다.
- 인증 링크는 화면이 `POST`로 확인한다(메일 보안 스캐너가 `GET` 링크를 미리 열어 한 번뿐인 토큰을 소진하는 일을 막는다).
- 로그인 실패 횟수는 **이메일 키**로 센다(없는 계정도 똑같이 세어 계정 존재 여부를 숨긴다).
- 테스트는 `as(memberId)` 헬퍼로 인증하게 해서 헤더 → 쿠키 전환이 헬퍼 한 곳에서 끝나게 한다(001 이후 모든 테스트에 영향).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: 001 plan과 같다 + **새 의존성 후보**: 보안(Spring Security 또는 `spring-security-crypto`), 메일(`spring-boot-starter-mail`). 선택은 research.md 결정 1·5. 헌법 기술 제약에 따라 plan에 필요한 이유를 한 줄로 적는다.

**Storage**: MySQL 8.4. 새 테이블 `pending_signup`, `login_session`, `login_attempt`, `member` 확장 ([data-model.md](data-model.md))

**Testing**: JUnit 5, Testcontainers(MySQL). 메일은 `MailSender` 이음새에 테스트용 구현을 꽂는다

**Target Platform**: 로컬 Docker. 운영 메일 발송 계정과 학교 이메일 도메인은 **사용자 준비물**이다

**Project Type**: web-service

**Performance Goals**: SC-010 로그인 95%가 1초 이내(BCrypt 비용 때문에 로그인이 가장 느린 요청이다), SC-002 가입 신청 95%가 1분 이내에 인증 메일 발송

**Constraints**: FR-014 헤더로는 어떤 요청도 인증되지 않음, FR-012 로그아웃 즉시 무효, FR-015(가입 신청 중복 방지)·FR-005 메일 한도(1분 간격, 24시간 5회)는 다중 인스턴스에서도 지켜져야 함, 비밀번호 원문은 어디에도 남지 않음

**Scale/Scope**: 기숙사 단위 수백~수천 명

## Constitution Check

*GATE: Phase 0 전 통과 필요. Phase 1 설계 후 재확인.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. 범위 상한 | 통과 | 이메일 로그인 하나. 소셜 로그인·탈퇴·비밀번호 재설정은 넣지 않았다. 4단계 항목이다 |
| II. 측정으로 증명 | 통과 (해당 없음) | 비교 측정 대상이 아니다 |
| III. 설명 가능한 설계 | 통과 (결정 대기) | 결정 12개 모두 후보·추천·설명 체크가 있다. 사용자 결정이 필요하다 |
| IV. 핵심 로직 테스트 | 통과 (해당 없음) | 필수 대상이 없다. 메일 한도의 동시성·잠금·토큰 단일 사용은 통합 테스트로 덮는다 |
| 기술 제약: 새 의존성은 이유를 한 줄로 | 대기 | Spring Security(또는 crypto)와 starter-mail의 이유는 research.md 결정 1·5에 적었다. 사용자 승인 후 확정 |
| 보안: 시크릿은 환경변수 | 통과 | 메일 계정, 허용 도메인은 환경 설정으로만 주입 |

## Project Structure

### Documentation (this feature)

```text
specs/002-login-signup/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── auth-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성 (구현 직전)
```

### Source Code (repository root)

```text
src/main/java/com/camgong/auth/                    # 새 최상위 패키지 (architecture 5절의 auth/)
├── AuthController.java        # signup, signup/resend, verify, login, logout, me
├── SignupService.java         # 신청(이메일만)·재발송·인증 완료(이름·비밀번호·동의를 받아 회원 생성), 메일 한도
├── LoginService.java          # 로그인·실패 횟수·잠금
├── SessionService.java        # 세션 발급·검증·슬라이딩·폐기
├── AuthFilter.java            # 쿠키 → 세션 → 현재 회원 (FR-014: X-Member-Id는 무시)
├── CurrentMember.java         # 컨트롤러 인자 리졸버로 쓰는 현재 회원
├── MailSender.java            # 인터페이스 (SmtpMailSender, LoggingMailSender 두 구현)
├── SmtpMailSender.java
├── LoggingMailSender.java     # 개발·테스트용: 발송 대신 기록
├── PendingSignup.java, LoginSession.java, LoginAttempt.java   # JdbcClient 결과 record (엔티티 아님, tasks T006)
├── AuthProperties.java        # 허용 도메인, 유지 기간, 잠금 기준, 메일 한도 등 설정
├── SessionCleanup.java        # 만료 세션·인증 요청 정리 (@Scheduled, 005 패턴)
│  ── 구현에서 추가된 파일 (T040 대조, 2026-10-28) ──
├── AuthConfig.java, Keys.java, Tokens.java            # 설정, 이메일·이름 정규화, 토큰 생성·SHA-256
├── SignupRequest/SignupResponse/ResendRequest/VerifyRequest/LoginRequest.java, AuthResponse.java, MemberView.java, Authenticated.java
├── VerificationMailRequested.java, VerificationMailListener.java   # AFTER_COMMIT + @Async 발송
├── SecurityConfig.java, OriginCheckFilter.java, ProblemResponses.java, AuthWebConfig.java
└── (삭제) common/MemberHeaderInterceptor.java — 001의 임시 헤더 인증 제거
src/main/java/com/camgong/member/                  # (수정) Member에 email, passwordHash, consent 컬럼
src/main/resources/
├── schema.sql                 # (수정) 새 테이블, member 확장
└── application.yml            # (수정) camgong.auth.* 설정
src/test/java/com/camgong/
├── support/IntegrationTestBase.java  # (수정) as(memberId) 인증 헬퍼, 메일 캡처
└── auth/
    ├── SignupIT.java          # 신청(이메일만)·검증 오류·중복·더블 제출·메일 한도
    ├── VerifyEmailIT.java     # 인증 성공·만료·사용됨·본문 검증 오류(토큰 미소진)·이름 충돌(토큰 유효)
    ├── LoginLogoutIT.java     # 로그인·실패 안내·로그아웃·다기기
    ├── LoginThrottleIT.java   # 5회 실패 잠금, 풀림, 없는 계정도 동일
    ├── SessionIT.java         # 14일 슬라이딩·만료
    ├── SignupDomainIT / ResendIT / OriginCheckIT / OriginCheckFilterTest / SessionServiceIT / SessionCleanupIT / AuthConfigIT / KeysTest   # 구현에서 추가
    ├── AuthGuardIT.java       # 보호 엔드포인트 전부 401, 헤더 위조 거부
    └── PrivacyIT.java         # 이메일·비밀번호가 응답·로그에 없음 (verify 요청의 비밀번호 포함)
```

**Structure Decision**: 인증은 공구 애그리거트와 무관한 독립 기능이라 최상위 패키지 `auth`를 둔다(회원 엔티티는 기존 `member`). `groupbuy.*`는 `auth`의 `CurrentMember`만 쓰고, 반대 의존은 없다.

## Complexity Tracking

| 항목 | 이유 | 더 단순한 안을 안 쓴 이유 |
|------|------|--------------------------|
| 인터페이스 `MailSender` + 구현체 2개 | 운영은 SMTP, 개발·테스트는 기록만 한다 | 구현체 하나로는 테스트에서 실제 메일을 못 막는다 |
