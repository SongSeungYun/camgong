# 설명 체크 모음 (구현 완료 후 일괄용)

> 작성 2026-10-28. 사용자가 "설명 체크는 구현이 다 끝난 뒤 몰아서 하겠다"고 했다. 이 문서는 사용자가 그때 한꺼번에 답할 질문과 근거를 모은 것이다.
>
> **출처와 신뢰도**
> - 아래 두 부분은 작업 세션이 자기 기록(SendMessage 보고, 사용자 창 설명, 독립 검수)에서 정리한 것이고, 백엔드 쪽은 서브에이전트가 transcript에서 추출했다. 관제탑이 내용을 전부 대조하지는 못했다. 의심 가는 곳은 해당 `research.md` 원문과 맞춰 본다.
> - 질문 문구는 `research.md` 원문을 우선했다. 세션이 질문을 다르게 썼으면 그 자리에 적혀 있다.
> - "기록 없음"은 근거를 못 찾았다는 뜻이다. 프론트 모범 답은 보낸 기록이 없어서 코드를 읽고 새로 쓴 초안이다.
> - 사용자가 직접 답해야 하는 결정 항목(후보·추천)은 [`docs/overnight/decisions-pending.md`](overnight/decisions-pending.md)에 따로 있다.

---

# 파트 1. 백엔드 (002~007)

사용자가 설명 체크를 나중에 일괄로 하므로, 백엔드 세션 기록에서 근거를 모았다. 지어낸 내용은 없고, 기록이 없는 항목은 `기록 없음`으로 적었다.

## 사용한 출처

1. 세션 transcript `cc588034-c4a2-4a46-a5f8-f73a22991781.jsonl`
   - `SendMessage`(수신 `camgong-b1`) 26건: 002 Phase 1 / T009 / Phase 2 / T013 / T014 / T014 정정 / Phase 3 / T021 / T022 / Phase 4 / T031 / T031 안 B 반영 / T034 / 002 Phase 6·7 / 002 push / 003 묶음 / 002 수정 / 003 수정 / 004 완료 / 004 검수 반영 / 005 완료 / 003·004·005 머지 / 007 완료 / 006·007 후속 / 낮음 4건. 이하 "SendMessage 보고"라고 부른다.
   - 같은 세션의 사용자 창 본문(어시스턴트 설명)과 독립 검수 서브에이전트 보고, 관제탑이 중계한 검수 메시지. 이하 "세션 창 본문", "독립 검수", "관제탑 검수"라고 부른다.
   - 이전 세션 transcript는 이 프로젝트 디렉터리에 없음(이 jsonl 하나뿐).
2. `origin/main`의 `specs/00N-*/tasks.md`(태스크 이름과 `(핵심 로직)` 표시), `specs/00N-*/research.md`(설명 체크 원문).
3. `origin/main`의 `docs/overnight/decisions-pending.md`, `docs/sessions/backend.md`. 둘 다 결정 현황과 세션 시작 지시일 뿐 태스크별 설명 체크 답은 없다.

## 읽는 법

- 설명 체크 질문은 research.md 원문을 썼다. 세션이 보낸 문구가 달랐으면 그 자리에 적었다.
- 002 외의 기능은 tasks.md가 태스크별 결정 번호를 지정하지 않는다. 세션이 결정 번호를 지정하지 않은 경우, 태스크 본문과 문항 내용이 맞는 research 문항을 "연결 후보"로 적었다. 이 연결은 이 문서를 만들면서 한 것이고 세션 기록이 아니다.
- 모범 답은 SendMessage 보고에 있는 것을 우선했다. 보고에 질문만 있고 답이 없는 002 T009·T013·T014·T021·T022는 같은 세션의 사용자 창 본문 설명을 따로 표시해 넣었다.
- 002는 이후 수정 커밋(결함 7건과 변경 B2, SendMessage 보고 "002 수정")으로 동작이 달라진 곳이 있어 "이후 수정"으로 따로 적었다.
- 006은 핵심 로직 승인 요약이 SendMessage로 나간 기록이 없다. 세션 창 본문, 독립 검수, 관제탑 검수만 남아 있다.

## 목차

- 002 로그인·가입: [T009](#002-t009-sessionservice) · [T013](#002-t013-signupservicesignup) · [T014](#002-t014-signupserviceverify) · [T021](#002-t021-loginservicelogin) · [T022](#002-t022-authfilter와-securityconfig) · [T031](#002-t031-재제출-갱신과-resend) · [T034](#002-t034-loginservicerecordfailure)
- 003 공구 목록·상세: [T005](#003-t005-displaystatus) · [T006](#003-t006-pagecursor) · [T011](#003-t011-groupbuysearchrepository) · [T012](#003-t012-groupbuyqueryservicelist) · [T018](#003-t018-groupbuyqueryservicedetail) · [T024](#003-t024-내-활동-조회)
- 004 공구 참여: [T007](#004-t007-pessimisticquantitygate) · [T012](#004-t012-participationservicejoin) · [T016](#004-t016-conditionalupdatequantitygate) · [T017](#004-t017-optimisticquantitygate) · [T018](#004-t018-nolockquantitygate) · [T021](#004-t021-participationserviceleave) · [T024](#004-t024-participationservicechange)
- 005 마감 확정: [T007](#005-t007-groupbuyfail) · [T008](#005-t008-groupbuyrepository-쿼리-두-개) · [T009](#005-t009-groupbuyclosercloseone) · [T011](#005-t011-groupbuycloseservicecloseexpired) · [T017](#005-t017-경계-규칙-점검)
- 006 개설자 체크: [T005](#006-t005-participationrepository-쿼리) · [T006](#006-t006-requirecheckable) · [T011](#006-t011-setpayment)
- 007 알림: [T007](#007-t007-notificationwriter) · [T008](#007-t008-notificationeventlistener) · [T013](#007-t013-notificationservice-읽음-처리)
- [기록 없음이 있는 태스크 목록](#기록-없음이-있는-태스크)

섹션은 모두 31개다. 질문 번호가 있는 "결정 N"은 해당 기능 research.md의 결정 번호다.

---

## 002 로그인·가입

### 002 T009 SessionService
- 목적: 로그인 세션을 발급·확인·슬라이딩 갱신·폐기하고 쿠키 도우미를 제공한다(research 결정 9·1).
- 핵심 동작:
  - `issue`는 SecureRandom 32바이트 URL-safe Base64 원문을 반환하고 DB(`login_session`)에는 SHA-256 hex만 저장한다.
  - `resolve`는 `last_used_at + ttl > now`일 때만 회원 id를 주고, 만료면 행을 지우고 empty를 돌려준다.
  - `touch`는 `last_used_at < now - 10분`일 때만 조건부 UPDATE로 쓴다(읽기 요청마다 쓰기가 생기지 않게). `revoke`는 멱등이다.
  - 쿠키는 `camgong_session`, HttpOnly, SameSite=Lax, Path=/, Secure는 설정값, Max-Age=14일. 시간은 모두 주입한 Clock.
- 설명 체크 질문:
  - 결정 9: "슬라이딩 갱신을 요청마다 하면 어떤 비용이 생기고, 10분 간격으로 줄이면 무엇을 포기하는가?"
  - 결정 1: "로그아웃을 즉시 무효로 만들려면 JWT가 왜 서버 상태를 필요로 하게 되는가?"
- 세션이 보낸 모범 답:
  - SendMessage 보고(T009 승인 요약)에는 질문과 답이 없고 동작·테스트·변이 검사만 있다. 설명은 사용자 창에서 했다고 Phase 2·3·4 묶음 보고에 적혀 있다.
  - 세션 창 본문, 결정 9: 요청마다 갱신하면 로그인 상태의 모든 요청이 읽기 하나에 `UPDATE login_session` 쓰기 하나를 더 하게 되고, 같은 세션 행에 쓰기가 몰려 락 경합이 늘며, 쓰기는 디스크·로그·인덱스 갱신 때문에 읽기보다 비싸다. 10분 간격으로 줄이면 `last_used_at`의 정확도를 포기한다(최대 10분 늦음, 14일 기준이라 만료 영향은 사실상 없음). 정확한 감사 기록으로는 쓸 수 없다. 한 줄: 정확한 시각 대신 쓰기 횟수를 얻는다.
  - 세션 창 본문, 결정 1(T022 때 설명): JWT는 서버가 아무것도 기억하지 않고 서명·만료만 보므로, 로그아웃해도 이미 발급된 토큰은 만료까지 유효하다. 즉시 무효화하려면 무효 토큰 목록(블랙리스트)을 서버가 들고 요청마다 확인해야 해서 결국 서버 상태가 생긴다. 어차피 서버 상태가 필요하면 세션을 DB에 두고 행 하나를 지우는 쪽이 단순하다. 이 프로젝트가 쓴 방식이다.
- 변이 검사·검수에서 확인한 것:
  - touch 간격 조건 제거 → 1개 실패, 만료 판정 제거 → 2개 실패. 테스트를 먼저 쓰고 컴파일 실패를 확인한 뒤 구현. 전체 56개 통과.
  - 독립 검수(코드만 전달): 결함 없음. 만료 삭제를 id만으로 하면 아주 드문 경합에서 방금 갱신된 행을 지울 수 있다는 지적을 조건 하나 추가로 반영(세션 창 본문, 검수 서술).

### 002 T013 SignupService.signup
- 목적: 가입 신청. 인증 전에는 회원을 만들지 않고 `pending_signup`에만 저장하고, 커밋 뒤 인증 메일 이벤트를 발행한다(research 결정 4·6).
- 핵심 동작:
  - 비밀번호 UTF-8 72바이트 초과면 400 `errors[password]`(BCrypt가 72바이트 뒤를 버리므로 글자 수가 아니라 바이트). `email_key`가 member에 있으면 409 `ALREADY_REGISTERED`(대소문자·공백 무시).
  - BCrypt 해시, 무작위 32바이트 토큰은 SHA-256 hex만 저장한다. 원문 비밀번호·토큰은 DB에 없다.
  - 이미 신청이 있으면 이 시점 구현은 429(재제출 갱신은 T031). 표시 이름은 이 시점에 검사·점유하지 않는다. 응답 `resendAvailableAt = now + 1분`.
  - 커밋 뒤 `VerificationMailRequested` 이벤트로 메일을 보낸다.
- 설명 체크 질문:
  - 결정 4 (research.md 원문): "같은 이름으로 두 사람이 인증을 마치려 할 때 `UNIQUE`가 어느 시점에 어디서 충돌하는가? (이름은 신청이 아니라 `POST /verify`의 `member` INSERT에서 정해진다.)"
  - (세션 정정본과 다름) 세션이 보낸 문구: "같은 이름으로 두 사람이 신청했을 때 UNIQUE가 어느 시점에 어디서 충돌하는가? (결정 4)". 현재 research.md는 변경 B2 뒤의 문구다.
  - tasks.md T001은 T013에 결정 4·6·8을 이어 놓았다. 결정 6: "같은 이메일의 재발송 요청이 동시에 두 개 오면 한도 판정이 어떻게 한 번만 통과시키는가?" 결정 8: "`endsWith("school.ac.kr")`로 도메인을 검사하면 어떤 우회가 가능한가?" 결정 13(변경 B2): "신청 때 비밀번호를 받으면 어떤 순서로 계정이 선점되고, 인증 때 받으면 왜 그 길이 막히는가?"
- 세션이 보낸 모범 답:
  - SendMessage 보고(T013)에는 질문만 있고 답이 없다. 세션 창 본문 설명(결정 4): 신청 단계에서는 같은 이름으로 둘이 신청해도 둘 다 성공한다(`pending_signup`에 표시 이름 유일 제약이 없고 이름을 점유하지도 않는다. 인증 전에 점유하면 메일만 받고 인증 안 한 사람이 이름을 오래 묶는다). 충돌은 `member`의 `display_name_key UNIQUE`에서 verify의 INSERT 시점에 난다. 먼저 인증한 쪽이 가져가고 나중 쪽은 409 `DISPLAY_NAME_TAKEN`. 토큰 소모(`used_at`)도 같은 트랜잭션이라 롤백돼 토큰이 유효한 채로 남고 다른 이름으로 다시 확인하면 가입이 끝난다.
  - SendMessage 보고 "002 수정"의 결정 4·6·13 답: 동시 재발송은 WHERE에 든 한도 조건이 행 잠금 뒤 재평가되어 한 건만 통과한다. 계정 선점은, 신청이 이름·비밀번호를 받지 않으니 공격자가 먼저 신청해도 링크를 누른 사람의 값으로 가입된다.
  - 결정 8(도메인 우회)에 대한 답: 기록 없음.
- 변이 검사·검수에서 확인한 것:
  - 테스트를 먼저 쓰고 13개 전부 실패 확인 후 구현. 변이: 72바이트 제한 제거 → `passwordLengthBoundaries` 실패, `ALREADY_REGISTERED` 검사 제거 → 해당 테스트 실패.
  - 독립 검수(코드만 전달): 막을 결함 없음. 반영 2건(비밀번호 `@NotBlank`→`@NotNull`, 이미 신청이 있을 때 429 경로 테스트 추가).
  - 이후 수정(SendMessage "002 수정"): 신청 본문은 `{email}` 하나로 바뀌고(변경 B2), 이메일·도메인 검사 → member 409 → 토큰 → `INSERT ... ON DUPLICATE KEY UPDATE id = id`(공유 잠금 교착 회피) → 0행이면 조건부 UPDATE(한도). 저장 주소로 발송(변이로 확인), 이메일 키 254자 초과는 400(변이), 이메일은 ASCII만 허용(켈빈 기호 U+212A 등은 정규화 키가 아니라 원문을 검사, 변이로 확인), 인증 메일 거부 시 마스킹한 수신자 로그.

### 002 T014 SignupService.verify
- 목적: 인증 링크의 토큰을 한 번만 쓰게 하면서 회원을 만들고 로그인 쿠키를 발급한다(research 결정 3·4).
- 핵심 동작:
  - 토큰 SHA-256 조회: 없으면 400 `INVALID_TOKEN`, `used_at`이 있으면 409 `TOKEN_USED`, 만료(`token_expires_at <= now`)면 410 `TOKEN_EXPIRED`.
  - `UPDATE pending_signup SET used_at ... WHERE id AND used_at IS NULL`을 member INSERT보다 먼저 실행하고 영향 행이 0이면 409 `TOKEN_USED`(동시 verify 중 하나만 통과, `useAffectedRows=true` 전제).
  - 이름·이메일 UNIQUE 위반은 예외를 던져 같은 트랜잭션이 롤백되고 `used_at`도 되돌아간다(토큰이 유효한 채로 남음). 이름 충돌은 409 `DISPLAY_NAME_TAKEN`, 이메일 충돌은 409 `ALREADY_REGISTERED`.
  - 성공하면 `SessionService.issue` + 쿠키(HttpOnly, SameSite=Lax). 인증 링크는 화면이 POST로 확인한다.
- 설명 체크 질문:
  - 결정 3 (research.md 원문): "메일 링크를 `GET`으로 직접 확인하게 하면 어떤 일이 생기고 `POST`가 왜 이를 막는가?"
  - 처음 보낸 승인 요약의 질문("같은 토큰으로 verify가 동시에 두 개 오면 어떻게 한 건만 통과하는가?")은 research에 없는 문장이어서 세션이 직접 정정해 위 문항으로 다시 보냈다. 최종 문구는 research.md와 같다.
  - tasks.md T001은 T014에 결정 3·4를 이어 놓았다. 결정 4 문항은 T013 항목 참조.
- 세션이 보낸 모범 답:
  - SendMessage 보고에는 질문만 있고 답이 없다. 세션 창 본문: GET으로 직접 확인하게 하면 메일 보안 스캐너나 링크 미리보기(메신저, 메일 클라이언트)가 사람이 누르기 전에 링크를 대신 열어 토큰이 소모된다. 사용자가 진짜로 누르면 `TOKEN_USED`를 보고 가입이 실패한 것처럼 보인다. POST를 쓰면 메일 링크는 프론트 화면 주소이고 화면이 열려도 아직 아무것도 소모되지 않는다. 사용자가 확인 버튼을 눌러야 화면이 `POST /api/auth/verify`를 보내고, 스캐너는 링크만 열 뿐 버튼은 누르지 않으니 토큰이 안 닿는다.
  - 동시 verify 8개에서 한 건만 통과하는 이유(처음에 잘못 물었던 질문의 답에 해당): 조건부 UPDATE(`AND used_at IS NULL`)의 영향 행이 1인 쪽만 통과하고 나머지는 `TOKEN_USED`(SendMessage 보고 T014 동작 항목).
- 변이 검사·검수에서 확인한 것:
  - 먼저 실패 확인 후 구현. 전체 74개 통과, 3회 재실행도 통과. 동시 verify 8개: 200 정확히 1건, 나머지는 모두 409 `TOKEN_USED`, member·login_session 각 1행.
  - 변이: 만료 판정 제거 → 실패. 토큰 소모 조건(`AND used_at IS NULL`) 제거 → 고정 시계에서는 통과(같은 값 재기록은 영향 행 0이라 우연히 가려짐). 테스트 시계를 일시적으로 흐르게 하니 동시 verify 테스트가 실패했고 실제 코드는 통과(원복 완료). 키 이름 파싱 변이도 새 테스트가 잡음.
  - 독립 검수(코드만 전달): 막을 결함 없음. 지적 반영: MySQL 오류 메시지 `Duplicate entry '<사용자 값>' for key ...`의 사용자 값에 키 이름이 섞이면 이름/이메일 충돌을 잘못 가를 수 있어 "for key" 뒤만 보도록 수정(예: `display_name_key@school.ac.kr` 이메일 충돌 테스트). 만료 1초 전 유효와 이름 대소문자 무시 테스트 보강.
  - 이후 수정(변경 B2): verify 본문이 `{token, displayName, password, consent, consentVersion}` 전부 필수. 순서는 ① 토큰 조회(400/409/410) → ② 본문 검증(400 `errors[]`, 토큰 소진 안 됨) → ③ `used_at` 선점(`token_hash` 조건 포함, 읽은 뒤 재발송으로 바뀐 옛 토큰은 소진 못 함) → ④ BCrypt + member INSERT → ⑤ 이름/이메일 충돌 409는 롤백으로 토큰 유지. 본문 오류 13종 각각 400+토큰 유지, 동시 verify 8건 → 200 정확히 1건, 정규화한 이름 키가 20자를 넘으면 400. 재검수: 결함 없음, 저중요 5건(토큰 해시 조건 채택, 임시 테이블 권한 안내 등).

### 002 T021 LoginService.login
- 목적: 이메일·비밀번호 로그인과, 계정 존재 여부를 드러내지 않는 실패 응답(research 결정 2·7의 로그인 쪽).
- 핵심 동작:
  - 이메일을 `Keys.normalize` 후 member 조회. 없거나, 인증 전 신청만 있거나, 비밀번호가 틀리거나, 72바이트 초과면 모두 같은 401 `INVALID_CREDENTIALS`(상태·title·detail 동일).
  - 없는 계정에는 미리 만든 더미 BCrypt 해시와 비교해 응답 시간 차이를 줄인다.
  - 성공하면 `SessionService.issue` + 쿠키. 비밀번호·이메일은 로그에 쓰지 않는다. 잠금은 US4(T035)에서 이 메서드에 끼웠다.
- 설명 체크 질문:
  - 결정 2 (research.md 원문): "비밀번호를 SHA-256 한 번으로 저장하면 안 되는 이유는?" 세션이 보낸 문구와 같다.
  - tasks.md T001은 T021에 결정 2·7을 이어 놓았다. 결정 7: "잠금을 `member` 컬럼에 두면 왜 계정 존재 여부가 드러나는가?"
- 세션이 보낸 모범 답:
  - SendMessage 보고에는 질문만 있고 답이 없다. 세션 창 본문: SHA-256은 일부러 빠르게 만든 해시라서, DB를 훔친 공격자가 GPU로 초당 수십억 개를 대입해 흔한 비밀번호를 금방 맞춘다. 소금이 없으면 같은 비밀번호가 같은 해시가 되어 레인보 테이블로 한 번에 풀린다. BCrypt는 일부러 느리고(비용 값으로 조절) 해시마다 소금이 들어가 대입 비용이 몇 자릿수 올라간다. 그래서 원문은 DB에 없고 BCrypt 해시만 저장한다.
  - 결정 7의 답(`member` 컬럼 잠금이 존재 여부를 드러내는 이유)은 T034 항목 참조.
- 변이 검사·검수에서 확인한 것:
  - 테스트를 먼저 쓰고 실패 확인. 틀린 비밀번호·없는 이메일·인증 전 이메일·73바이트 비밀번호의 응답 본문이 완전히 같음을 단언. 전체 105개 통과.
  - 세션이 직접 적은 한계: 더미 해시 비교(응답 시간 균등화)는 성능 특성이라 변이 검사에서 지워도 테스트가 통과한다. 이후 수동 측정(시나리오 22, cost 12): 존재 계정 틀린 비밀번호 평균 258ms vs 없는 이메일 평균 259ms(각 80회)로 차이가 없었다.
  - 독립 검수(코드만 전달) 2라운드: 로그인 열거 누출 없음, 결함 없음.
  - 이후 수정: 254자 초과 이메일은 DB 기록 없이 일반 401(더미 해시 비교), 로그인 성공 시 요청에 실린 이전 세션 쿠키 행만 revoke(실패한 로그인은 이전 세션 유지).

### 002 T022 AuthFilter와 SecurityConfig
- 목적: 쿠키 세션으로 요청자를 식별하고, 보호된 `/api/**`는 비로그인에 401 `UNAUTHENTICATED`로 막고, `X-Member-Id`는 어디서도 인증하지 않는다(FR-014, research 결정 1·10).
- 핵심 동작:
  - 필터 순서는 Origin 검사 → AuthFilter → 인가. AuthFilter는 `camgong_session` 쿠키 → SessionService → 회원을 SecurityContext에 넣는다(STATELESS).
  - permitAll은 POST signup, signup/resend, verify, login, logout과 GET `/api/groupbuys` 목록뿐이고 나머지 `/api/**`는 인증 필요. 인증 실패는 필터 단계에서 MVC와 같은 모양의 ProblemDetail(`errors: []`).
  - 인증이 본문 검증(400)·존재 확인(404)보다 먼저라 항상 401이 이긴다.
  - Origin 검사: 상태를 바꾸는 요청의 Origin(없으면 Referer)이 같은 출처이거나 allowed-origins에 있어야 하고, 둘 다 없으면 통과, 다르면 403 `FORBIDDEN_ORIGIN`. 세션이 실제로 갱신된 요청에서만 쿠키를 다시 내려 Max-Age 14일을 유지.
- 설명 체크 질문:
  - 결정 10 (research.md 원문): "테스트 전용 우회가 운영 보안에 어떤 위험이 되는가?" 세션이 보낸 문구는 "테스트 전용 우회(헤더 인증)를 운영에 남기면 보안에 어떤 위험이 되는가? (결정 10)"이고 뜻은 같다.
  - 덧붙인 결정 1 (research.md 원문): "로그아웃을 즉시 무효로 만들려면 JWT가 왜 서버 상태를 필요로 하게 되는가?"
- 세션이 보낸 모범 답:
  - SendMessage 보고에는 질문만 있고 답이 없다. 세션 창 본문, 결정 10: `X-Member-Id: 1`만 붙이면 누구든 1번 회원으로 행세할 수 있어 인증이 없는 것과 같다. 테스트에서만 켜도 운영과 테스트가 서로 다른 코드를 타서, 테스트가 통과해도 운영 인증 경로는 검증된 적이 없다. 설정 한 줄 실수나 프로파일 착오로 운영에서 열릴 수 있다. 그래서 우회를 없애고 `as()`가 실제 세션 쿠키를 만들어 붙이게 했다.
  - 결정 1의 답은 T009 항목 참조(JWT 로그아웃과 서버 상태).
- 변이 검사·검수에서 확인한 것:
  - 필터가 쿠키를 무시하게 하면 48개 실패, Origin 검사를 끄면 3개 실패. 전체 105개 통과(001의 44개도 쿠키 인증으로 통과). `grep -rn "X-Member-Id" src/main` 비어 있음, src/test에는 AuthGuardIT의 위조 시도만.
  - 독립 검수 2라운드: 1차 결함 3건 → 수정 후 2차 결함 없음. (a) 쿠키 Max-Age가 슬라이딩되지 않던 문제, (b) Origin 비교가 대문자 호스트·기본 포트에 오탐하던 문제(정규화), (c) `CurrentMember` null이 500이 될 잠재 문제(401로).
  - 알려둔 한계: (c)는 공개 엔드포인트가 아직 없어 테스트가 없었다(003 이후에도 목록이 CurrentMember를 받지 않아 추가 대상이 없다고 보고함). 리버스 프록시가 TLS를 끝내면 쓰기 요청이 전부 403이라 `ALLOWED_ORIGINS` 설정이 필요. 로그인 시 이전 세션 쿠키 미폐기는 이후 수정에서 폐기하도록 바뀜.

### 002 T031 재제출 갱신과 resend
- 목적: 이미 신청이 있는 이메일의 재제출과 재발송을, 한도(1분 간격 + 24시간 5회) 판정과 갱신을 한 문장으로 처리한다(research 결정 6).
- 핵심 동작:
  - 신청 INSERT를 `ON DUPLICATE KEY UPDATE id = id`로 바꿨다. 중복 키를 예외로 받으면 공유 잠금이 남아 동시 UPDATE끼리 교착할 수 있기 때문이다. 중복이면 행 X 잠금을 잡고 0행을 돌려받는다.
  - 0행이면 조건부 UPDATE 한 문장으로 한도 판정과 갱신을 같이 한다(1분 간격 + 24시간 5회, 창 리셋 포함, `SET` 평가는 왼쪽→오른쪽). `token_hash` 교체로 이전 토큰이 무효가 된다.
  - 영향 행 0이면 `FOR UPDATE` 잠금 읽기로 이유를 가른다. 행 없음/`used_at` 있음 → resend 404, signup 409. 그 외는 429 + `retryAt` + `Retry-After`(초, 올림). 일반 SELECT는 REPEATABLE READ 스냅샷이 오래된 값을 보여서 잠금 읽기를 썼다.
  - 창 리셋을 `<`가 아니라 `<=`로 했다(`retryAt` 정확한 시각에 재시도하면 통과해야 해서).
- 설명 체크 질문:
  - 결정 6 (research.md 원문): "같은 이메일의 재발송 요청이 동시에 두 개 오면 한도 판정이 어떻게 한 번만 통과시키는가?" 세션이 보낸 문구와 같다.
- 세션이 보낸 모범 답:
  - SendMessage 보고(T031): 한도 조건이 UPDATE의 WHERE에 들어 있어서 판정과 갱신이 한 문장이다. 두 요청이 동시에 와도 InnoDB 행 잠금으로 직렬화되고, 먼저 온 쪽이 `last_sent_at`을 지금으로 바꾼 뒤에야 뒤쪽이 조건을 다시 평가한다. 이미 1분 안이라 0행이 되고 429를 받는다.
- 변이 검사·검수에서 확인한 것:
  - `SET` 평가 순서(spikes #10)는 MySQL 8.4에서 왼쪽→오른쪽이 맞다. 두 줄 순서를 뒤집은 변이, 1분 간격 조건·일일 한도 조건·`used_at IS NULL` 조건을 뺀 변이를 테스트가 모두 잡았다.
  - 동시 같은 신청 8개 → 202 정확히 1건 + 나머지 429, 행 1개, 메일 1통. 동시 resend 8개도 같다.
  - 독립 검수 3회, 마지막 회차 결함 없음. 검수 결함 1건 수정: verify가 읽은 뒤 재발송으로 토큰이 바뀌어도 소진할 수 있었다(claim UPDATE에 `token_hash` 조건 추가).
  - 설계 문제: 인증 전 재제출이 이름·비밀번호를 덮어쓰면 공격자가 피해자 이메일로 자기 비밀번호를 넣어 재신청할 수 있다. 안 A(스펙 그대로) / 안 B(토큰·한도만 갱신) 중 사용자가 B를 선택해 반영(b71ba1e). 덮어쓰기를 되살리는 변이는 ResendIT 11개 중 2개가 실패. 이후 변경 B2로 신청 본문이 이메일뿐이 되어 UPDATE가 토큰·한도 컬럼만 갱신. 재발송은 저장된 주소(`FOR UPDATE`로 읽음)로 발송하고 응답 email도 저장값(변이로 확인).

### 002 T034 LoginService.recordFailure
- 목적: 로그인 실패를 이메일 키로 세어 5번째 실패에 15분 잠그고, 없는 이메일도 똑같이 센다(research 결정 7).
- 핵심 동작:
  - `INSERT ... ON DUPLICATE KEY UPDATE` 한 문장으로 실패를 센다. 로그인은 트랜잭션 밖이라 바로 커밋되어 뒤따르는 401에 롤백되지 않는다.
  - VALUES를 참조하지 않고 파라미터만 써서 별칭(AS new)이 필요 없다. `SET`은 왼쪽→오른쪽이라 `locked_until`이 갱신된 `failed_count`를 본다(4→5번째에 잠김).
  - 스펙 공식 수정: data-model의 `locked_until = IF(failed_count >= 5, now+15분, locked_until)` 그대로면 잠금이 풀린 뒤 이전 `locked_until`이 남아 횟수가 1로 되돌아가 5에 못 닿는다. 그래서 횟수가 최대 이상이면 (NULL이거나 만료됐을 때만 새 시각, 아니면 유지), 아니면 (만료됐으면 NULL, 아니면 유지)로 바꿨다. 이미 잠긴 상태에서 늦게 도착한 실패가 잠금을 연장하지 않는다.
  - T035: 이메일 정규화 직후 `login_attempt`를 조회해 `locked_until > now`면 비밀번호 검사 없이 429 `LOGIN_LOCKED`(`retryAt`, `Retry-After`). 성공하면 행 삭제로 횟수 초기화.
- 설명 체크 질문:
  - 결정 7 (research.md 원문): "잠금을 `member` 컬럼에 두면 왜 계정 존재 여부가 드러나는가?"
  - (세션 정정본과 다름) 세션이 보낸 문구: "5번 틀린 뒤 6번째 시도가 올바른 비밀번호여도 거부되는 이유와, 없는 이메일도 똑같이 세는 이유는? (결정 7)". 같은 결정의 다른 측면이다.
- 세션이 보낸 모범 답:
  - SendMessage 보고(T034), 세션이 보낸 문구에 대한 답: 잠금 검사가 비밀번호 비교보다 앞에 있어서 맞는 비밀번호로도 못 뚫는다(안 그러면 잠금 중에 대입해 계속 시도할 수 있다). 없는 이메일도 세지 않으면 "잠기는 이메일 = 가입된 이메일"로 계정 존재 여부가 드러나므로, 존재 여부와 상관없이 이메일 키로 똑같이 센다.
  - research.md 원문 문항(잠금을 `member` 컬럼에 두면)에 대한 직접 답: 기록 없음. 가장 가까운 기록은 위 SendMessage 답의 뒷부분(없는 이메일도 세지 않으면 존재 여부가 드러난다)이다.
- 변이 검사·검수에서 확인한 것:
  - 먼저 red 확인(7개) 후 구현, 전체 131개 통과(LoginThrottleIT 9개). 변이: 만료 후 NULL 리셋 제거, 만료 후 횟수 1 리셋 제거, 잠금 판정 필터 제거를 각각 테스트가 잡았다. 연장 방지 조건은 얼어붙은 시계에 가려 처음엔 살아남아서, `recordFailure`를 직접 호출해 늦은 실패를 흉내 내는 테스트를 추가한 뒤 잡혔다.
  - 동시 10번 틀린 로그인: 401을 받은 수와 `failed_count`가 정확히 같고(손실 없음), 잠금 시각은 정확히 한 번 정해진다.
  - 독립 검수 1회에서 결함 1건(254자 초과 이메일이 `login_attempt.email_key`에 못 들어가 500) → `@Size(max = 254)`로 400 처리. 한계로 적은 것: 이메일 키로 세므로 남이 일부러 틀려서 15분 막을 수 있다(수용, IP 단위는 주석에 언급).
  - 이후 수정(SendMessage "002 수정", 가장 큰 변경): 시도를 비밀번호 비교 전에 한 문장(INSERT … ON DUPLICATE KEY UPDATE)으로 센다. 잠겨 있으면 영향 행 0 → BCrypt 없이 429. 50건 동시 틀린 비밀번호 → 정확히 5건만 비교(401), 45건은 429, `failed_count=5`. 4번 틀리고 5번째에 맞으면 성공. `updated_at`을 SET 맨 앞에 둠. 변이(선계수 제거, 잠금 중 비계수 제거, 임계값 `>=`→`>`)를 모두 테스트가 잡음. 재검수 에이전트(코드 읽기): 5번째 시도가 잠금을 걸되 비밀번호 비교로 진행하고, 잠긴 동안은 영향 행 0, 만료 후 첫 시도는 1로 리셋됨을 확인.

---

## 003 공구 목록·상세·내 활동

### 003 T005 DisplayStatus
- 목적: 저장된 `status`와 `deadline`·`now`로 표시 상태(OPEN/PENDING_CLOSE/COMPLETED/FAILED)를 서버에서 계산한다.
- 핵심 동작:
  - `derive(status, deadline, now)`: `OPEN` + `deadline > now` → OPEN, `OPEN` + `deadline <= now` → PENDING_CLOSE, COMPLETED/FAILED는 그대로. `CANCELLED`가 넘어오면 예외.
  - 쿼리 필터용으로 같은 조건을 Criteria 조건(`toPredicate`)으로도 만든다. 둘이 같은 경계(`deadline <= now`면 PENDING_CLOSE)를 쓴다.
  - `now`는 마이크로초로 맞춘다(DB `DATETIME(6)`과 같은 정밀도). 목록 쿼리는 `status <> CANCELLED`를 항상 추가.
- 설명 체크 질문: 기록 없음
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 독립 검수(목록 단계): 결함 없음. `derive`와 `toPredicate`가 같은 `now`·같은 경계를 쓴다고 확인. 확인 불가로 남긴 저위험 1건(리눅스에서 `now`의 나노초와 DB 마이크로초 사이 경계)은 `now`를 마이크로초로 자르는 것으로 반영.
  - 003 수정 8건: COMPLETED·FAILED 필터에 마감이 지난/미래 시드를 모두 넣어 술어에 `deadline`이 섞이는 변이를 잡음. 변이 검사 결과를 T005 단독으로 적은 다른 기록은 없다.

### 003 T006 PageCursor
- 목적: 키셋 페이지네이션용 불투명 커서를 만들고, 깨진 커서나 정렬이 다른 커서를 400으로 막는다.
- 핵심 동작:
  - JSON → Base64URL. 최신순은 `id`, 마감 임박순은 `(deadline, id)`를 담는다.
  - 깨졌거나 요청 `sort`와 맞지 않거나 날짜가 2000~2100 밖이면 400 `errors[cursor]`.
  - 마감 임박순 커서는 마이크로초까지 보존한다(`Instant` 왕복).
- 설명 체크 질문:
  - 결정 4 (research.md 원문): "마감 임박순에서 `deadline`이 같은 공구가 여러 개일 때 `id`를 함께 쓰지 않으면 무슨 일이 생기는가?" 세션이 보낸 문구와 같다.
- 세션이 보낸 모범 답:
  - SendMessage 보고(003 묶음): 커서가 deadline만이면 같은 마감 묶음이 페이지 경계에 걸릴 때 `deadline > d`는 나머지를 영영 건너뛰고 `>=`는 이미 본 것을 다시 준다. 그래서 `(deadline, id)` 쌍을 쓴다.
- 변이 검사·검수에서 확인한 것:
  - 테스트 `sameDeadlineAcrossAPageBoundary`가 id 조건을 빼면 실패함을 변이로 확인.
  - 마이크로초 마감(같은 마감 3개씩)이 섞인 마감 임박순 순회(size 5와 1)에서 중복·누락 없음, 커서가 마이크로초를 잘라내는 변이를 잡음. `/api/me/groupbuys`에 마감 임박순 커서를 보내면 400.
  - 독립 검수: 깨진 커서 종류들이 모두 400 `errors[cursor]`로 가는 것을 확인. 범위 밖 날짜(`Instant.parse`가 ±1e9년까지 받음)가 500이 될 수 있다는 미확인 지적 → 2000~2100 밖이면 400으로 반영.

### 003 T011 GroupBuySearchRepository
- 목적: JPA Criteria로 표시 상태·카테고리·검색어·키셋 조건과 정렬을 동적으로 조립한다.
- 핵심 동작:
  - 검색은 단어마다 `(title LIKE OR item_name LIKE)`를 AND로 연결하고, `%`·`_`·`\`를 이스케이프해 `ESCAPE '\'`로 전달한다.
  - 키셋: 최신순 `id < :cursorId`, 마감 임박순 `(deadline > :d OR (deadline = :d AND id > :id))`. `setMaxResults(size + 1)`, 카운트 쿼리 없음, 항상 `status <> 'CANCELLED'`. 카드 컬럼만 DTO 프로젝션.
  - 검색어는 앞 5단어만 쓰고 나머지는 무시(계약에 오류 규정이 없어 세션이 정함, 관제탑이 확정). 전각 공백(U+3000)·NBSP를 공백으로 보고, 50자 제한은 코드포인트 기준.
- 설명 체크 질문:
  - 결정 3 (research.md 원문): "`LIKE '%단어%'`가 인덱스를 못 타는데도 이 규모에서 괜찮다고 판단한 근거는 무엇이고, 어느 시점에 바꿔야 하는가?" 세션이 보낸 문구와 같은 뜻이다.
- 세션이 보낸 모범 답:
  - SendMessage 보고(003 묶음): 필터 후 PK 역방향 스캔이 `LIMIT 21`에서 일찍 끝나서 흔한 단어는 빠르고, 일치가 없으면 전체를 훑는다. 1만 건 실측에서도 9ms 안쪽이다. 수만 건 이상이거나 드문 검색어 p95가 느려지면 FULLTEXT(ngram)로 바꾼다.
  - 이어서 실측: 마감 임박순은 `(status, deadline)` 인덱스가 없어 전체 스캔 + filesort(005가 추가), 드문 검색어(결과 0건)는 끝까지 훑는다(EXPLAIN으로 확인).
- 변이 검사·검수에서 확인한 것:
  - 정규식에서 전각 공백·NBSP 두 문자를 빼는 변이를 잡음. 5단어 `limit` 제거 변이를 잡음. COMPLETED·FAILED 필터에 마감 조건이 섞이는 변이를 잡음. `%`·`_`·`\` 이스케이프 테스트가 실제 MySQL에서 통과.
  - 독립 검수: 이스케이프 순서(`\` 먼저), 반복 파라미터(`?size=1&size=2`) 400, 반복 `q` 등 확인. 지적한 낮은 위험(전각 공백이 단어에 남음)은 수정 8건으로 반영.

### 003 T012 GroupBuyQueryService.list
- 목적: 키셋 쿼리 결과를 `size`건으로 자르고 `nextCursor`를 만들며 표시 상태를 붙인다.
- 핵심 동작:
  - `size + 1`건 중 `size`건을 돌려주고 초과분이 있으면 `PageCursor`로 `nextCursor`, 없으면 `null`.
  - 표시 상태는 `Instant.now(clock)`(마이크로초 절단) 기준으로 `DisplayStatus.derive`를 적용. 읽기 전용 트랜잭션.
  - 비로그인 접근이 가능한 유일한 엔드포인트(`GET /api/groupbuys`).
- 설명 체크 질문: 기록 없음
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 독립 검수: 결함 없음. `size+1` 판정과 마지막 페이지의 `nextCursor = null`이 맞다고 확인.
  - 변이: 내 활동 정확히 `size`건일 때 `nextCursor` null(두 탭)·`size-1`이면 non-null을 테스트하고 `>` → `>=` 변이를 잡았다(SendMessage "003 수정", 내 활동 쪽 단언이라 T012 목록 자체의 변이로 적은 기록은 없다). 마이크로초 마감 순회 테스트(중복·누락 없음)가 목록 페이지 경계도 지킨다.

### 003 T018 GroupBuyQueryService.detail
- 목적: 상세 조회에서 회원 확인, 오픈채팅 링크 공개 규칙, 개설자 전용 명단을 처리한다.
- 핵심 동작:
  - 회원 확인(없으면 401), 공구가 없거나 `CANCELLED`면 404. 내 참여와 `amount = unitPrice × quantity`, `participantCount`(개설자 포함).
  - 오픈채팅 링크는 data-model 표대로: 개설자는 FAILED 제외 항상(OPEN·PENDING_CLOSE·COMPLETED), 참여자는 COMPLETED만, 그 외 `null`. (tasks.md T018 문구는 개설자를 OPEN·COMPLETED로 적어 data-model과 차이가 있어 data-model 우선으로 구현, 관제탑이 tasks T014를 data-model 기준으로 고침.)
  - 명단 쿼리는 개설자일 때만 실행(I9). 개설자의 `myParticipation`은 quantity·amount만(확인 시각 키 없음), 일반 참여자는 `paidConfirmedAt`·`receivedConfirmedAt` 키가 있고 null.
- 설명 체크 질문: 기록 없음
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 독립 검수(상세 단계): 결함 없음. 링크 공개 4가지 경우(`openChatLinkFollowsTheVisibilityTable`), 명단은 개설자일 때만 조회, 개설자/참여자/비참여자의 `myParticipation` 모양, 401 vs 404, 금액 오버플로 없음(unitPrice 100만, totalQuantity 1000)을 확인.
  - 변이 검사: 기록 없음(묶음 요약은 "변이 검사로 핵심 조건 확인"이라고만 적었다).
  - N+1 단계 기록(`docs/benchmarks/browse-n-plus-one.md`): B 상세 명단 N=1/20/50 쿼리 수 ① 4/23/53 → ② fetch join 4/4/4 → ③ batch size 4/5/5 → ④ DTO 프로젝션 4/4/4. 최종 ④, `QueryCountIT` 상한(개설자 상세 ≤4, 개설자 외 ≤3)이 회귀를 지킨다.

### 003 T024 내 활동 조회
- 목적: 내가 연 공구와 내가 참여한 공구를 키셋으로 조회한다(`GET /api/me/groupbuys?role=`). N+1을 일부러 남긴 순진한 구현에서 시작해 단계별로 개선한다.
- 핵심 동작:
  - 내가 연 공구: `organizer_id = :me AND status <> 'CANCELLED' [AND id < :cursor] ORDER BY id DESC LIMIT size+1`. 내가 참여한 공구는 `participation`을 거쳐 `g.id DESC` 키셋.
  - 처음은 항목마다 `participation.getGroupBuy()`를 지연 로딩해 `1 + N` 쿼리. 이후 fetch join → batch size → DTO 프로젝션으로 줄여 최종은 DTO 프로젝션.
  - 최종 쿼리 수는 내가 참여한 공구 1건(N=1/20/50 모두), 상세 명단 4건. `QueryCountIT`가 상한(내 활동 ≤2)을 회귀로 지킨다.
- 설명 체크 질문:
  - 연결 후보(세션이 이 태스크에 직접 지정한 기록은 없음) 결정 11 (research.md 원문): "fetch join과 batch size는 같은 N+1을 각각 어떻게 줄이고, 어떤 경우에 batch size가 더 나은가?"
  - 연결 후보 결정 1: "개선 후 코드만 남겨도 "전후 비교"가 증명되려면 문서에 무엇이 있어야 하는가?"
- 세션이 보낸 모범 답:
  - SendMessage 보고(003 묶음)에 위 질문들에 대한 답 서술은 없다. 가장 가까운 기록은 측정 결과 서술: 내가 참여한 공구 ① 2/21/51 → ② fetch join 1/1/1 → ③ batch size 2/2/2 → ④ DTO 프로젝션 1/1/1. batch size는 IN 한 번으로 묶여 N이 100을 넘으면 `ceil(N/100)`번(spikes 확인 결과). 문서는 단계마다 커밋 해시를 적는다(`e38d78f`, `23e6e5f`, `2a35d39`, `5ad73aa`, `74ab096`는 독립 검수가 확인한 해시).
- 변이 검사·검수에서 확인한 것:
  - 변이: `nextCursor` 경계(`>` → `>=`)를 잡음(내 활동 정확히 size건/size-1). 키셋 순회 테스트(`keysetPagingVisitsEveryItemOnceInBothTabs`)가 두 탭을 순회.
  - 독립 검수 2회: 결함 없음. 지적한 테스트 약점은 `doesNotExist()`가 JSON null도 통과시킨다는 점(raw body 단언 권장), 다른 사람 공구를 섞은 참여자 탭 순회 미검증, 개설자 탭에는 쿼리 수 상한 없음. 두 번째 검수: 키셋 쿼리의 `g.id`와 `p.groupBuyId` 일치, 제거된 리포지토리 메서드의 잔존 호출 없음, `QueryCountIT`가 N+1 재발을 실제로 잡음(지연 로딩이면 1+N으로 `<=2` 위반)을 확인.

---

## 004 공구 참여

### 004 T007 PessimisticQuantityGate
- 목적: 비관적 락(`SELECT ... FOR UPDATE`)으로 공구 행을 잡고 수량을 예약하는 기본 게이트(운영 기본 방식은 2026-10-28 사용자 결정으로 pessimistic).
- 핵심 동작:
  - `findByIdForUpdate`로 행을 잡고 순서대로 검사: 없거나 CANCELLED → `NOT_FOUND`, `status != OPEN` 또는 `deadline <= now` → `NOT_JOINABLE`, 가격 불일치 → `PRICE_CHANGED`, 용량 초과 → `INSUFFICIENT_QUANTITY`. 통과하면 `secured += delta`, `completeIfFull()`로 COMPLETED 전환, `updatedAt = now`.
  - 용량 비교는 int 오버플로를 피하려 `delta > total - secured` 뺄셈 비교로 통일했다(4개 게이트 공통).
  - 마감 판정 시각은 락을 잡은 뒤 다시 읽은 Clock(`max(requestedAt, now)`)으로 한다(005 단계에서 관제탑 검수 지적으로 반영).
  - 락 순서는 항상 공구 행 → 참여 행(001 수정·취소, 005 확정과 같음).
- 설명 체크 질문:
  - 연결 후보(세션이 이 태스크에 직접 지정한 기록은 없음) 결정 1 (research.md 원문): "왜 이 구조에서만 4방식 차이가 한 클래스 안에 갇히는가?" (QuantityGate 4구현 T007·T016·T017·T018 전체에 걸린 문항)
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 변이: 비관적 `FOR UPDATE`를 `findById`로 바꿔도 `@Version` 덕에 통과(낙관적처럼 동작해 안전, SendMessage "004 완료"). `secured + delta` 비교로 되돌리는 변이를 테스트가 잡음(Integer.MAX_VALUE 계열 케이스).
  - 경쟁 테스트: 마지막 자리 100명, 다양한 수량, 이중 요청, 혼합 +/−. 마지막 자리 경쟁 1,000라운드 pessimistic 통과(82초). 측정(S1/S2/혼합)에서 I1 위반 0·초과 0·완료 이벤트 1회·5xx 0.
  - 독립 검수(게이트 단계): 결함 없음, `race.repeat=150`에서도 flake 없음. 관제탑 검수: 초과 판매·갱신 손실로 이어지는 결함 없음. 지적 반영: int 오버플로, 락 대기 타임아웃은 재시도 없이 즉시 BUSY(교착은 재시도), 재시도 표현 통일, 같은 회원 이중 요청 테스트를 결정적 겹침(Overlap 훅)으로.

### 004 T012 ParticipationService.join
- 목적: 참여 신청. 개설자 403, 멱등 재시도, 게이트 예약, 참여 행 삽입, 모집 완료 이벤트까지 한 트랜잭션에서 처리한다.
- 핵심 동작:
  - 순서: (1) 개설자면 403 (2) 이미 참여 행이 있으면 수량이 같을 때만 200, 다르면 409 `ALREADY_JOINED` (3) 게이트 `reserve(+quantity, expectedUnitPrice, now)` (4) 참여 행 삽입 (5) `completed`면 개설자 포함 모든 참여자 id로 `GroupBuyCompleted` 발행. 공구 FAILED/마감/완료면 `NOT_JOINABLE`이 `ALREADY_JOINED`보다 우선.
  - `UNIQUE (group_buy_id, member_id)` 위반(같은 회원의 동시 이중 요청)은 트랜잭션을 롤백(예약도 함께 되돌림)하고 다시 처리해 (2)로 들어간다. 재시도는 반드시 새 트랜잭션(최대 3회 시도), 락 대기 타임아웃(1205)은 즉시 BUSY, 교착(1213)은 재시도.
  - 사전 검사는 엔티티가 아닌 값 조회(`GroupBuyHead`)로 해서, 락 전 스냅샷이 `FOR UPDATE` 결과를 가리는 영속성 컨텍스트 문제를 피한다.
- 설명 체크 질문:
  - 연결 후보(세션이 이 태스크에 직접 지정한 기록은 없음) 결정 4 (research.md 원문): "참여 행 삽입이 실패(중복)하면 이미 올라간 `secured`는 어떻게 되돌려지는가?"
  - 연결 후보 결정 3: "마감 직전에 도착했지만 처리 시각이 마감 이후인 참여 요청은 왜 거부되고, 그것이 005와 어떻게 모순되지 않는가?"
- 세션이 보낸 모범 답:
  - 위 질문에 대한 명시적 답 서술은 없다. SendMessage 보고(004 완료)의 가장 가까운 서술: 같은 회원 동시 이중 요청은 UNIQUE 위반(`uq_participation_group_member`만 재시도 대상)을 롤백(예약 포함)하고 다시 처리하며, 뒤 요청은 거부가 아니라 "같은 수량이면 200"이 된다(검수가 잡은 결함, 거부 직후 내 참여 행을 잠금 읽기로 재확인해 바뀌었으면 재시도). 낙관적/재시도 문항의 답: 같은 트랜잭션 안에서 재시도하면 REPEATABLE READ 스냅샷이 그대로라 의미가 없어서 재시도는 반드시 새 트랜잭션.
- 변이 검사·검수에서 확인한 것:
  - 1차 독립 검수 결함 3건: (1) 동시 이중 요청이 200이 아니라 잘못된 409를 받을 수 있음(거부 직후 잠금 읽기 재확인으로 수정), (2) `1.5`가 1로 잘림(Jackson `accept-float-as-int=false` 전역 적용으로 400), (3) 다른 `DataIntegrityViolationException`까지 재시도로 오인(`uq_participation_group_member`만 재시도).
  - 변이 실험(Overlap 훅으로 결정적 겹침 뒤): UNIQUE 재시도 제거 → 2개 실패, `lockedMine` 수량 필터 제거 → 2개 실패, 거부 직후 재확인 블록 제거 → 2개 실패. 락 타임아웃: LockTimeoutIT(1초대에 BUSY), ParticipationRetryTest(낙관적 충돌 정확히 3회 시도 후 BUSY, 락 타임아웃 1회 시도 후 BUSY, 교착 3회 시도, 2회째에 풀리면 성공). 변이(timeout 분기 제거, `attempt > MAX`)를 잡음.
  - POST 이미 참여 + 다른 수량에서 FAILED/마감/완료면 `NOT_JOINABLE` 우선(테스트).

### 004 T016 ConditionalUpdateQuantityGate
- 목적: 한 문장 `UPDATE`가 조건 판정과 갱신을 같이 하는 방식(왕복이 가장 적음).
- 핵심 동작:
  - `UPDATE group_buy SET secured_quantity = secured_quantity + :delta, status = IF(secured_quantity = total_quantity, 'COMPLETED', status), version = version + 1, updated_at = :now WHERE id AND status='OPEN' AND deadline > :now AND unit_price = :expectedUnitPrice AND ...`(취소는 가격 조건 생략). MySQL은 `SET`을 왼쪽→오른쪽으로 평가해 `status`가 이미 갱신된 `secured`를 본다.
  - 수량 한도 조건은 SQL 쪽 BIGINT 산술이라 오버플로가 없고 `diagnose`만 뺄셈 비교로 수정.
  - 영향 행 0이면 이유를 `FOR SHARE`로 진단한다(스냅샷이 아니라 최신 행). 이유 순서는 pessimistic과 같다(CANCELLED → COMPLETED/FAILED → 마감 → 가격 → 수량). 직접 UPDATE도 `version`을 올린다(낙관적 방식과 맞물리게).
- 설명 체크 질문: 기록 없음 (research.md 결정 5에는 설명 체크 문항이 없고, 세션 보고는 "결정 5, 조건부 UPDATE"라는 이름으로 답만 적었다.)
- 세션이 보낸 모범 답:
  - SendMessage 보고(004 완료, "설명 체크(결정 5, 조건부 UPDATE)"): 한 문장이 상태·마감·가격·남은 수량을 WHERE로 판정하고, InnoDB 행 잠금이 같은 공구의 UPDATE를 직렬화한다. 기다린 쪽은 최신 값으로 조건을 다시 평가해 영향 행 0이 되어 거부된다. 0행이면 이유를 `FOR SHARE`로 진단(스냅샷이 아니라 최신 행).
- 변이 검사·검수에서 확인한 것:
  - 변이: 수량 한도 조건 제거, `version` bump 제거를 잡음(004 완료 보고). `secured + delta` 비교로 되돌린 `diagnose` 변이도 잡음.
  - 독립 검수(게이트 단계): `SET` 순서·WHERE의 old 값 사용·음수 delta·null 가격·`version` bump 모두 맞다고 확인. 지적한 테스트 약점 "`version = version + 1`을 빼는 변이를 잡는 테스트가 없다"는 이후 변이 잡힘으로 보고됨. 마지막 자리 경쟁 1,000라운드 통과(85초). 측정: pessimistic과 지연이 같음(S1 p95 70.0/75.7ms).

### 004 T017 OptimisticQuantityGate
- 목적: `@Version`으로 읽고 갱신 시 `WHERE version = ?`로 충돌을 감지하는 낙관적 방식.
- 핵심 동작:
  - 충돌하면 최대 3회 시도(첫 시도 + 재시도 2번), 짧은 무작위 대기(5~25ms, 백오프 없음) 후 새 트랜잭션에서 재시도하고 소진하면 409 `BUSY`.
  - 모든 쓰기가 `version`을 올리는 규칙을 지킨다(직접 UPDATE인 조건부 방식과 맞물림).
  - 같은 트랜잭션 안에서 재시도하면 REPEATABLE READ 스냅샷이 그대로라 의미가 없으므로 재시도는 반드시 새 트랜잭션.
- 설명 체크 질문:
  - 연결 후보(세션이 이 태스크에 직접 지정한 기록은 없음) 결정 6 (research.md 원문): "개설자의 수정(락 보유)과 낙관적 방식의 참여가 겹치면 누가 기다리고 누가 재시도하는가?"
  - 세션 보고가 "낙관적/재시도"로만 지칭한 문항의 원문 기록 없음.
- 세션이 보낸 모범 답:
  - SendMessage 보고(004 완료, "설명 체크(낙관적/재시도)"): 같은 트랜잭션 안에서 재시도하면 REPEATABLE READ 스냅샷이 그대로라 의미가 없어서 재시도는 반드시 새 트랜잭션이다.
  - 결정 6 문항에 대한 답: 기록 없음.
- 변이 검사·검수에서 확인한 것:
  - 독립 검수(게이트 단계): `saveAndFlush`의 `ObjectOptimisticLockingFailureException`이 재시도 루프에 잡히고, 매 시도가 새 `tx.execute`이며, `group_buy`에 쓰는 모든 경로가 `version`을 올린다고 확인. 관제탑 검수 요구(BUSY 경계 변이를 잡는 테스트)는 `attempt > MAX` 변이를 잡는 `ParticipationRetryTest`로 반영.
  - 측정: optimistic은 S1에서 빠르나 S2에서 재시도 1,684회·BUSY 12건, 혼합 부하(같은 행에 쓰기 집중)에서 요청의 약 50%(41,969/83,242)가 BUSY로 거부 → 이 서비스 부하에 부적합. 단 이는 "3회 시도·5~25ms 무작위 대기·백오프 없음" 조건의 결과(문서에 명시). 마지막 자리 경쟁 1,000라운드 통과(74초).

### 004 T018 NoLockQuantityGate
- 목적: 벤치마크 대조군. 락 없이 읽고 자바에서 검사해 갱신 손실이 실제로 생기는 것을 보인다.
- 핵심 동작:
  - 일반 `SELECT`로 읽고 자바에서 검사한 뒤 `secured = 읽은 값 + delta`로 쓴다. 갱신 손실로 `secured ≠ Σ 수량`이 생기는 것이 기대 동작.
  - `benchmark` 프로파일에서만 활성화된다. 알 수 없는 값이거나 `none`을 비벤치 프로파일에서 켜면 기동 실패(QuantityGateConfigTest).
  - 설정 키는 `camgong.quantity-gate`.
- 설명 체크 질문: 기록 없음
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 대조군이 실제로 깨짐: 경쟁 테스트가 `none`을 깨뜨림(임시 서브클래스로 확인). 측정: `none`은 S1 30회 전부 깨짐(성공 합계 446건, I1 위반 30/30, 모집 완료 이벤트 ≠1회 30/30), S2 수용 수량 평균 131(한도 10).
  - 독립 검수(게이트 단계)가 지적: `none`이 비벤치에서 거부되는지, 대조군이 실제로 I1을 깨는지를 보호하는 테스트가 없었다 → QuantityGateConfigTest와 대조군 확인으로 보강. 벤치 검수: `/bench/*`는 benchmark 프로파일에서만, `none`은 비벤치 거부 확인.

### 004 T021 ParticipationService.leave
- 목적: 참여 취소. 개설자 403, 참여 행 없음 404, 수량을 게이트로 돌려놓고 참여 행을 삭제한다.
- 핵심 동작:
  - 게이트 `reserve(−기존 수량, null, now)` 후 참여 행 삭제(한 트랜잭션). 거부 사유는 T008 매핑.
  - 이중 DELETE는 게이트 뒤 `lockedMine` 잠금 읽기로 사전 수량과 대조하고, 어긋나면 롤백해 두 번 차감을 막는다(재시도하면 404).
  - `delta`가 음수여도 상태·마감 검사는 통과해야 한다(취소는 가격 비교 없음).
- 설명 체크 질문:
  - 연결 후보(세션이 이 태스크에 직접 지정한 기록은 없음) 결정 2 (research.md 원문): "001에서 공구 취소를 `DELETE`가 아닌 `POST .../cancel`로 한 것과 여기서 참여 취소를 `DELETE`로 한 것이 왜 다른가?"
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 독립 검수(1차): 이중 DELETE에서 두 번째 요청의 게이트가 차감해도 `lockedMine`이 행이 없음을 발견해 `RetryableConflict`로 롤백 → `secured_quantity`는 두 번 차감되지 않고 재시도는 404 `PARTICIPATION_NOT_FOUND`가 된다고 확인.
  - 변이: `lockedMine` 수량 필터 제거 → 2개 실패(이중 요청 테스트 4종 중 `LeaveParticipationIT` 포함). 경쟁: `ParticipationSerialization*`(취소/수정과 참여 동시)를 게이트 3종에서 실행.

### 004 T024 ParticipationService.change
- 목적: 참여 수량 변경. `delta = 새 수량 − 기존 수량`으로 게이트를 통과시켜 수량을 바꾼다.
- 핵심 동작:
  - 개설자 403, 참여 행 없음 404. `delta`로 게이트 `reserve(delta, expectedUnitPrice, now)`, 참여 행 `quantity` 갱신, 완료되면 `GroupBuyCompleted` 발행.
  - 같은 수량 PUT(`delta = 0`)은 이후 단순화되어 게이트를 지나지 않는다. 사전 검사에서 FAILED면 409 `NOT_JOINABLE`, 취소 404, 그 외는 가격 확인·쓰기 없이 현재 상태로 200(완료·마감 뒤에도 200). 이벤트 재발행 없음, `version`·`updated_at` 불변.
  - 이중 PUT은 `lockedMine`으로 사전 수량과 대조하고 어긋나면 롤백한다.
- 설명 체크 질문: 기록 없음
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 변이: `delta 0`도 게이트로 보내는 변이 → 6개 테스트 실패(SendMessage "004 검수 지적 반영 2"). 같은 수량 PUT이 완료·마감 뒤에도 200인 테스트(6→15 완료 후 같은 본문 재전송), 가격이 바뀐 뒤 같은 수량 PUT 200 테스트.
  - 1차 독립 검수 결함: 동시 이중 PUT(2→4)이 남은 수량 부족일 때 잘못된 409를 받는 문제(잠금 읽기 재확인으로 수정). 관제탑 검수: delta=0도 게이트를 지나 `notJoinable`이 먼저 걸리던 문제를 같은 수량 PUT 규칙(완료·마감 뒤 200, FAILED 409)으로 수정. 혼합 +/− 경쟁 테스트가 mixed 부하를 지킨다.

---

## 005 마감 확정

### 005 T007 GroupBuy.fail
- 목적: 모집 기간이 끝나도 정원이 안 찬 공구를 `FAILED`(종료 상태)로 전이한다.
- 핵심 동작:
  - `status == OPEN`일 때만 `FAILED`로 바꾸고 `updatedAt = now`. 그 외 상태(또는 확보 = 총 수량)면 `IllegalStateException`(불변식 I7, I10).
  - `FAILED`에서 나가는 전이는 없다. 이 전이는 `FAILED`를 읽어야 해서 `GroupBuyStatus.FAILED`는 003에서 먼저 추가되었다.
- 설명 체크 질문: 기록 없음
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 변이 검사: 기록 없음(T007 단독으로 적은 변이는 없다. 005 변이 3건은 claim 조건·후보 제외 목록·스케줄러 호출).
  - 독립 검수 결과: 종료 상태 하나 보장, 이벤트 AFTER_COMMIT 등을 이상 없음으로 확인. `fail()`은 "두 번째 방어선"으로 언급됨(`fail()` 자체를 직접 지적한 항목은 없다).

### 005 T008 GroupBuyRepository 쿼리 두 개
- 목적: 마감된 공구를 후보로 찾고, 건별로 행 락을 잡아 선점한다(중복 방지는 행 락 + `SKIP LOCKED`).
- 핵심 동작:
  - 후보 id 조회(락 없음): `status='OPEN' AND deadline <= :cutoff AND secured_quantity < total_quantity ORDER BY deadline LIMIT :batchSize`. 이후 이미 시도한 id를 `NOT IN` 제외 목록으로 뺀다(영구 실패 후보가 앞을 막는 굶김 방지).
  - 건별 선점(네이티브): `SELECT * FROM group_buy WHERE id = :id AND status = 'OPEN' AND deadline <= :cutoff AND secured_quantity < total_quantity FOR UPDATE SKIP LOCKED`. 영속 엔티티로 돌아온다. `:cutoff = now − 1초`.
  - 선점은 반드시 id 하나를 지정한다. 범위 + `LIMIT 1`은 REPEATABLE READ 갭 락 때문에 다른 워커의 `status` 갱신이 락 대기 시간 초과로 실패했다(spikes 5').
- 설명 체크 질문:
  - 결정 1 (research.md 원문): "두 인스턴스가 같은 공구를 동시에 잡으려 하면 각각 무슨 일이 일어나고, 이미 확정된 공구를 늦게 온 인스턴스가 다시 처리하지 않는 이유는 무엇인가?"
  - (세션 보고는 결정 1·2·3을 T017 점검 항목 아래 묶어 제시했고 태스크별 연결은 적지 않았다. T008과의 연결은 쿼리 내용 기준이다.)
- 세션이 보낸 모범 답:
  - SendMessage 보고(005 완료, 설명 체크 결정 1): 두 인스턴스가 같은 공구를 동시에 잡으면 먼저 락을 잡은 쪽만 행을 얻고 다른 쪽은 `SKIP LOCKED`로 즉시 빈 결과(SKIPPED)를 받는다. 이미 확정된 공구는 조건 `status='OPEN'`이 락 안에서 거짓이라 빈 결과가 되어 다시 처리되지 않는다(이벤트도 같은 트랜잭션이라 한 번).
- 변이 검사·검수에서 확인한 것:
  - 변이: claim의 `status` 조건 제거, 후보 제외 목록 제거를 테스트가 잡음(스케줄러 호출 제거 변이는 T011).
  - 독립 검수: 결함 1건(영구 실패 후보가 배치 앞쪽을 막아 뒤쪽 후보를 굶김) → `NOT IN` 제외로 수정, 테스트 추가(한 배치 이상의 영구 실패 후보가 뒤를 굶기지 않음). claim 쿼리의 stale-read·락 문제 없음 확인. 2차 검수: 결함 없음(제외 목록 최대 batchSize × maxRounds = 기본 5000, 비었을 때 -1 센티넬).
  - EXPLAIN: 후보 조회가 `idx_group_buy_status_deadline` range 사용. 네이티브 `SKIP LOCKED`가 영속 엔티티로 돌아옴을 `claimForClose` 테스트로 확인.

### 005 T009 GroupBuyCloser.closeOne
- 목적: 공구 한 건을 새 트랜잭션에서 선점해 `FAILED`로 확정하고 `GroupBuyFailed` 이벤트를 한 번 발행한다.
- 핵심 동작:
  - 서비스와 다른 클래스의 빈이다. `@Transactional`로 선점 쿼리(T008) 실행 → 비어 있으면 `SKIPPED` → 있으면 `fail(now)`, 개설자 포함 모든 참여 행의 회원 id를 잠금 읽기로 읽어 `GroupBuyFailed(eventId, groupBuyId, groupBuyTitle, recipientIds)` 발행(같은 트랜잭션, 커밋 후 소비), `CLOSED` 반환.
  - 참여자는 선점 뒤 읽으므로 `findLockedByGroupBuyId` 같은 잠금 읽기를 쓴다(일반 SELECT는 REPEATABLE READ에서 선점 대기 전 스냅샷을 볼 수 있음).
  - 수신자는 개설자를 먼저 넣은 `LinkedHashSet`(중복 없음).
- 설명 체크 질문:
  - 결정 3 (research.md 원문): "`closeOne`이 서비스와 같은 클래스의 메서드였다면 건별 트랜잭션이 왜 시작되지 않는가?"
- 세션이 보낸 모범 답:
  - SendMessage 보고(005 완료, 설명 체크 결정 3): 같은 클래스 안의 메서드 호출은 프록시를 우회해 `@Transactional`이 무시되므로, `closeOne`을 다른 빈으로 분리해야 건마다 새 트랜잭션이 열린다.
- 변이 검사·검수에서 확인한 것:
  - 스파이크 확인: 별도 빈 `@Transactional`이 건마다 새 트랜잭션을 여는 것을 건별 격리 테스트로 확인(spikes 표시용 보고).
  - 독립 검수: 이벤트가 같은 트랜잭션에서 발행되고 AFTER_COMMIT으로 받아 롤백 시 나가지 않음, 수신자가 distinct이고 개설자 포함이며 eventId·제목을 담음을 확인. 관제탑 검수 반영: `recipients`에 `g.getOrganizerId()`를 명시적으로 합침, WARN 로그에 예외 스택트레이스 포함(`CloseResilienceIT`가 단언).
  - 테스트: 한 건 실패 격리 후 재시도 성공 시 이벤트 1회(CloseResilienceIT), 이벤트 수신자·제목·eventId(CloseExpiredIT).

### 005 T011 GroupBuyCloseService.closeExpired
- 목적: 마감된 후보를 배치로 순서대로 확정하는 라운드 루프(스케줄러가 30초 `fixedDelay`로 호출).
- 핵심 동작:
  - `cutoff = now − margin`으로 후보 id를 `batch-size`씩 조회하고 후보마다 `GroupBuyCloser.closeOne`을 호출. 마감 시각이 이른 공구부터 처리. `CloseStats`를 돌려준다.
  - 라운드 반복 조건: 후보가 없으면 끝, 한 라운드에서 확정한 공구가 0건이면 끝(후보가 전부 다른 인스턴스에 잡혀 있을 때 무한 반복 방지), 최대 `max-rounds`. 이미 시도한 id는 `NOT IN`으로 제외.
  - 건별 예외를 격리한다(로그 `close-run`/`close-failed`).
- 설명 체크 질문:
  - 결정 2 (research.md 원문): "`fixedRate` 대신 `fixedDelay`를 쓴 이유는?" (스케줄러 `CloseExpiredScheduler` 설정에 관한 문항이다. 세션이 T011에 직접 연결한 기록은 없고 같은 묶음에서 제시되었다.)
- 세션이 보낸 모범 답:
  - SendMessage 보고(005 완료, 설명 체크 결정 2): `fixedRate`는 이전 실행이 길어지면 실행이 겹치거나 밀려서 몰리지만, `fixedDelay`는 끝난 뒤 간격을 세므로 한 인스턴스 안에서 실행이 겹치지 않는다.
- 변이 검사·검수에서 확인한 것:
  - 변이: 스케줄러 호출 제거를 테스트가 잡음(`CloseSchedulerWiringTest`: 켜면 스스로 확정, 끄면 빈 미등록). 후보 제외 목록 제거 변이도 잡음.
  - 독립 검수 1차: 결함 1건(굶김)을 `NOT IN`으로 수정. 2차: 결함 없음(사소한 위생 2건 반영). 관제탑 검수 반영: `CloseProperties` `@Min` 검증(`batchSize<=0`이면 매 실행 예외, `maxRounds=0`이면 조용히 무동작 방지), WARN 스택트레이스.
  - 테스트: 5개 워커 동시 100개 → 확정·이벤트 정확히 1회 반복, 겹친 실행, 재실행 무변화, 다른 트랜잭션이 잡은 행은 기다리지 않고 건너뜀, 밀린 50건 마감 순서, 250건 > 배치 3라운드.
  - 측정(close-batch.md): 50개(마감 15~90초 분산) 모두 확정, 지연 평균 15.2초·최대 30.1초(목표 1분 충족). 같은 순간 마감 1,000건은 3.4초(10라운드), 그동안 k6 목록 p95 1.6ms·상세 p95 3.5ms.

### 005 T017 경계 규칙 점검
- 목적: 참여·수정·취소의 마감 판정(`deadline <= now`)과 확정의 마감 판정(`deadline <= now − margin`)이 요청 처리 순간의 Clock으로, 공구 행 락 안에서 이루어져 종료 상태가 정확히 하나만 남는지 확인한다.
- 핵심 동작:
  - 참여·수정·취소는 `deadline <= now`부터 즉시 거부, 확정은 claim에서 `deadline <= now − 1초`를 락 안에서 재확인한다. 마감~확정 사이(최대 약 31초)는 "마감됨(확정 대기)".
  - 둘이 같은 공구 행 락에서 직렬화되므로 종료 상태는 정확히 하나다.
  - 처음 점검 결과는 코드 수정이 필요 없다는 것이었으나, 관제탑 검수가 "참여·수정의 `now`가 락 대기 전에 고정됨"을 짚어 이후 수정했다(아래).
- 설명 체크 질문:
  - 연결 후보(세션이 이 태스크에 직접 지정한 기록은 없음) 004 research 결정 3 (research.md 원문): "마감 직전에 도착했지만 처리 시각이 마감 이후인 참여 요청은 왜 거부되고, 그것이 005와 어떻게 모순되지 않는가?"
  - 005 research.md에는 이 태스크에 해당하는 설명 체크 문항이 따로 없다(005 결정 1~3은 T008·T009·T011 항목 참조).
- 세션이 보낸 모범 답:
  - SendMessage 보고(005 완료)의 T017 점검 결과: 참여·수정·취소는 `deadline ≤ now`에서 즉시 거부(게이트 3종과 001 `isModifiable` 모두 락 안에서 요청 처리 순간 Clock으로 판정), 확정은 claim에서 `deadline ≤ now−margin(1초)`을 락 안에서 재확인한다. 둘이 같은 공구 행 락에서 직렬화되므로 종료 상태는 정확히 하나다.
  - 이후 SendMessage 보고(003·004·005 머지): 락 받은 뒤 시각으로 마감을 판정하도록 수정(pessimistic 게이트와 001 `lockModifiable`; conditional/optimistic은 락 대기만큼 오차라고 문서에 명시).
- 변이 검사·검수에서 확인한 것:
  - 관제탑 검수 중(중) 지적: 마감 D, 개설자 PUT이 D−0.1초에 `now`를 잡고 락을 2초 기다리는 동안 D+1초가 지나도 stale `now`로 `isModifiable`을 통과해 마감을 연장할 수 있다(FR-009·T017 불충족). 반영: 락 뒤 `Instant.now(clock)`을 다시 읽음.
  - 테스트: 순서를 결정적으로 고정한 `CloseOrderedRaceIT`(두 순서 모두), `LockWaitDeadlineIT`(락 대기 중 마감이 지나가는 경우). 경쟁 테스트 1,000회: 마지막 자리 853/147, 취소 902/98, 연장 862/138로 양쪽 순서가 모두 발생. 처음 `CloseRaceIT`는 확정이 항상 이겨 퇴화해 있어 0~20ms 무작위 지연으로 고침.
  - 독립 검수: 락 뒤 시각 판정이 pessimistic 모드에서 맞고, 확정 쪽은 라운드 시작 cutoff가 실제 시각보다 이르므로 post-lock join이 열려 있다고 보는 공구를 닫지 않는다고 확인. `LockWaitDeadlineIT`는 수정이 없으면 join/연장/취소가 201/200/204가 되어 실패한다고 확인. 지적한 약점: `CloseOrderedRaceIT`는 두 closer를 순서화하지 않으므로 다중 인스턴스 중복 방지 검증 문구에서 빼야 함, `LockWaitDeadlineIT` 풀 미종료, join 케이스의 `sleep(500)` 가정(문서·테스트 위생으로 반영).

---

## 006 개설자 체크

### 006 T005 ParticipationRepository 쿼리
- 목적: 체크표 목록·요약·입금/수령 지정과 되돌리기 쿼리를 한 곳에 둔다.
- 핵심 동작:
  - 체크표 목록은 개설자를 제외(`p.member_id <> :organizerId`), `ORDER BY p.id`, 필터는 `filter=unpaid`(`paid_at IS NULL`)·`unreceived`(`received_at IS NULL`). 003이 N+1을 푼 최종 방식을 그대로 쓴다(구현은 별도 `CheckQueryRepository`의 4개 쿼리).
  - 요약은 개설자 제외 전체 기준으로 필터와 무관하게 계산(`COUNT(*)`, `COUNT(paid_at)`, `COUNT(received_at)`, `SUM(quantity)`, ...).
  - 입금/수령은 별도 UPDATE 네 개: `SET paid_at = COALESCE(paid_at, :now)` / `SET paid_at = NULL`(수령도 같은 모양). 서로 영향을 주지 않는다.
  - 락 읽기가 필요 없다: COMPLETED는 종료 상태라 참여 행·공구 행이 이후 바뀌지 않고 락 대기도 없어 스냅샷이 낡을 일이 없다(tasks.md T005 설명). 단 이 문장은 이후 검수에서 정정되었다(아래).
- 설명 체크 질문:
  - 연결 후보(세션이 이 태스크에 직접 지정한 기록은 없음, research.md 원문):
    - 결정 3: "시각 컬럼 하나가 불리언 + 시각 두 컬럼보다 안전한 이유는?"
    - 결정 4: "체크표가 003 명단과 다른 쿼리를 쓰면 어떤 위험이 있는가?"
    - 결정 5: "요약 숫자는 필터와 무관하게 전체 기준인데, 필터를 서버에서 하면 요약은 어떻게 계산하는가?"
  - 세션 보고는 "006 결정 1·2"를 설명 체크 대기 항목으로만 적었다(T011 항목 참조).
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 세션 창 본문(구현 요약): 변이 6건(COALESCE 제거, 403/409 순서 뒤집기 등)을 모두 잡았다(변이 6건 전체의 내역과 태스크별 배분은 적혀 있지 않다). 측정: 참여자 50명 체크표 p95 5.3ms(목표 1초).
  - 관제탑 검수 반영(PR #9): 테스트 전용 DataSource 프록시(`JdbcStatementCounter`, 의존성 추가 없음)로 체크표가 정확히 3문장(머리 + 행 + 요약)임을 단언. 요약을 행마다 JdbcClient로 계산하는 변이는 실패 확인. `CheckQueryRepository` javadoc의 "락 대기도 없어 스냅샷이 낡을 일이 없다" 서술을 정정(구성은 불변이지만 `paid_at`/`received_at`은 COMPLETED 뒤에도 바뀌므로 응답은 `FOR SHARE` 재읽기). 001/004에 COMPLETED 불변식 테스트가 없어 `CompletedImmutableIT` 추가(COMPLETED에서 수정·취소·참여·수량 변경·참여 취소 모두 409이고 group_buy/participation 행이 version까지 그대로). `isModifiable` 변이로 실패 확인. 후속 낮음 4건에서 `NOT_MODIFIABLE`/`NOT_JOINABLE` + `reason=COMPLETED`까지 단언.

### 006 T006 requireCheckable
- 목적: 체크표 접근 검사. 개설자가 아닌 사람이 공구 상태를 알아내지 못하게 403을 409보다 먼저 판정한다.
- 핵심 동작:
  - 순서: 공구 없음 또는 CANCELLED → 404, 요청자가 개설자가 아니면 403 `ORGANIZER_ONLY`, 상태가 COMPLETED가 아니면 409 `NOT_CHECKABLE`. 비로그인 401은 002의 보호 필터가 처리.
  - `NOT_CHECKABLE`에는 현재 상태를 `reason`으로 싣지 않는다(오류 코드 표에 reason 정의가 없음).
  - 잘못된 본문(400)은 접근 검사보다 먼저 판정(계약에 한 줄 명시, 지금 동작을 테스트로 고정).
- 설명 체크 질문: 기록 없음 (006 research.md 결정 8은 오류 코드 결정이고 설명 체크 문항이 없다.)
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 변이: 403/409 순서를 뒤집는 변이를 포함한 6건을 모두 잡았다(세션 창 본문의 구현 요약, 태스크별 배분은 적혀 있지 않다).
  - 독립 검수: 접근 순서(404 → 403 → 409, 401은 기존 필터)와 비개설자가 공구 상태를 알 수 없음을 확인. 잘못된 `filter`의 400이 접근 검사 뒤에 와 비개설자에게 새지 않음, 개설자 대상 400 `INVALID_TARGET`, 비참여자 404를 확인.
  - 관제탑 검수(PR #7 머지 후): 접근 제어·멱등·동시성·계약 필드·개인정보는 이상 없음. 반영: 참여자 아님 404 title을 `PARTICIPATION_NOT_FOUND`로 통일(`INVALID_TARGET` 400도 title로), 다른 공구에만 참여한 회원 번호 PUT → 404 테스트(`findOne`의 `groupBuyId` 조건을 빼는 변이는 실패).

### 006 T011 setPayment
- 목적: 개설자가 참여자의 입금 여부를 "목표 상태 지정"으로 설정하고, 갱신된 행을 `CheckRow`로 돌려준다(수령 확인도 같은 모양).
- 핵심 동작:
  - `requireCheckable` → `memberId`가 개설자면 400 `INVALID_TARGET` → 참여 행이 없으면 404 → `confirmed`에 따라 지정/되돌리기 UPDATE 하나 실행(같은 트랜잭션) → 갱신된 행을 `CheckRow`로 반환. 이벤트를 발행하지 않는다(FR-008).
  - 영향 행 0은 "이미 그 상태"일 수 있어(`useAffectedRows=true`) 영향 행 수로 오류를 만들지 않는다.
  - 공구 행 락은 잡지 않는다(COMPLETED는 종료 상태). 대신 응답 재읽기는 `FOR SHARE` 잠금 읽기로 한다(아래 검수 결함).
- 설명 체크 질문:
  - 연결 후보(세션 보고가 "006 결정 1·2"를 대기 항목으로 적었으나 문구와 태스크 연결은 적지 않음) research.md 원문:
    - 결정 1: "토글이 아니라 목표 상태를 지정하는 방식이 더블 클릭에서 왜 안전한가?"
    - 결정 2: "공구 행 락을 잡지 않아도 되는 이유는 무엇이고, 004의 참여에서는 왜 잡았는가?"
- 세션이 보낸 모범 답: 기록 없음
- 변이 검사·검수에서 확인한 것:
  - 변이: `COALESCE` 제거를 포함한 6건을 모두 잡았다(세션 창 본문). 후속(PR #9): 동시 요청 테스트(`CheckConcurrencyIT`)가 시계를 10분 흘려 첫 확정 시각이 유지되는지 단언하게 고쳐, `COALESCE(paid_at, :now)`를 `paid_at = :now`로 바꾸는 변이를 잡게 됨.
  - 독립 검수 결함 1건(낮음~중간): 동시 요청이 내 UPDATE를 "변화 없음"으로 만들면, 뒤따르는 일반 SELECT가 REPEATABLE READ의 낡은 스냅샷을 읽어 응답 본문이 틀린 값을 보일 수 있다(DB 상태는 맞음). 잠금 읽기(`FOR SHARE`)로 수정하고 `HeadPause` 훅으로 결정적으로 재현하는 테스트(`CheckStaleResponseIT`)를 추가, 잠금 읽기를 일반 읽기로 되돌리는 변이를 확인. 재검수: 결함 없음(자기 UPDATE가 이미 X 잠금을 쥐고 있어 `FOR SHARE`가 막히거나 교착할 수 없음).
  - 멱등 확인: 지정·되돌리기 문장이 멱등이고 입금과 수령은 서로 다른 컬럼이라 덮어쓰지 않음. `Participation`의 `paid_at`/`received_at`은 `insertable=false, updatable=false`라 004 수량 변경 flush가 null로 덮지 않음.

---

## 007 알림

### 007 T007 NotificationWriter
- 목적: 알림 행을 청크 배치로 대량 삽입하고, 실패와 중복을 안전하게 처리한다.
- 핵심 동작:
  - `write(List<NotificationRow>)`를 `insert-chunk-size`(500)씩 나눠 `INSERT ... ON DUPLICATE KEY UPDATE id = id`를 JDBC 배치로 실행한다. `UNIQUE (event_id, recipient_id)`가 중복을 막는다(영향 행 수에 기대지 않음). 각 청크는 자기 트랜잭션.
  - 청크 하나가 실패하면 그 청크만 행마다 단건 삽입으로 격리한다. 실패한 행은 최대 `retry-attempts`(3)번 지수 대기(1초, 4초, 16초)로 다시 시도하고, 그래도 실패하면 WARN(`notification-failed eventId=… recipientId=… reason=…`)을 남기고 포기한다. 독 행 1개는 격리된다.
  - 행별 재시도가 DB 장애 시 실행기를 몇 시간 붙잡는 문제(검수 지적) 때문에, 연속 2행 포기하면 그 이벤트의 나머지를 포기한다(`notification-aborted eventId= written= unwritten= total=` WARN). 카운터는 청크 안으로 한정(청크 경계를 넘는 포기 쌍은 중단 안 함). 성공한 청크는 카운트를 리셋.
  - 한계: 결정적 실패(FK 등)가 인접 2행이면 뒤 수신자도 유실(허용 한계로 기록).
- 설명 체크 질문:
  - 연결 후보(세션이 이 태스크에 직접 지정한 기록은 없음, research.md 원문):
    - 결정 2: "연속으로 두 번 수정했을 때 두 알림이 서로 다른 사건으로 구분되는 이유는? 같은 이벤트를 재처리했을 때 한 번만 들어가는 이유는?"
    - 결정 3: "청크 하나가 실패했을 때 그 청크를 단건으로 다시 넣는 것이 왜 중복을 만들지 않는가?"
    - 결정 4: "재시도가 같은 알림을 두 번 만들지 않는 이유는?"
  - 세션 보고는 "007 결정"을 설명 체크 대기 항목으로만 적었다.
- 세션이 보낸 모범 답: 기록 없음 (SendMessage 보고는 동작 요약만: "청크 500 배치 → 실패 청크만 행별 재시도(1s·4s·16s), 독 행 1개는 격리".)
- 변이 검사·검수에서 확인한 것:
  - 스파이크 T005: `ON DUPLICATE KEY UPDATE id = id` 배치 INSERT는 `rewriteBatchedStatements` 없이 동작하고 중복 방지 OK. 측정: 알림 1000건 생성 0.42초, 취소 요청(콜드) 0.11초, 목록 p95 3.95ms.
  - 1차 독립 검수: 행별 재시도가 실행기를 오래 붙잡는 문제(중간) → 연속 2행 포기 규칙. 2차 검수: 연속 카운터가 성공한 청크 뒤에도 리셋되지 않는 버그 → 성공 청크 리셋, 회귀 테스트 + 변이 검사 완료(`aSuccessfulChunkBetweenTwoGiveUpsResetsTheConsecutiveCount`, 리셋이 없으면 약 499행이 유실되어 실패한다고 3차 검수가 확인). 청크 안으로 카운터 한정, 재시도 대기가 base·4·16배인지 단언(`pause()` 훅), 내용 불변 수정은 알림 없음.
  - 관제탑 검수: `insert-chunk-size=1`이면 청크 안 카운터가 0에서 시작해 중단 불가 → `@Min(2)` + 기동 거부 테스트(`NotificationPropertiesTest`). 벤치마크 문서 한계 절에 실패 경로·메일 풀 영향 미측정 추가.
  - 독립 검수 지적(반영 확인): 두 인접 독 행이 건강한 뒤 수신자를 유실하는 한계는 허용 한계로 기록. 로그 `written/unwritten/total`로 유실 규모 파악 가능. 같은 eventId 재처리 시 유니크로 중복이 생기지 않음.

### 007 T008 NotificationEventListener
- 목적: 네 이벤트를 받아 알림 행을 만들고 본 트랜잭션에 영향을 주지 않고 쓴다.
- 핵심 동작:
  - 네 이벤트를 `@TransactionalEventListener(phase = AFTER_COMMIT)`으로 받아 전용 `notificationExecutor`에서 `NotificationRow`를 만들고 `NotificationWriter.write`를 호출한다.
  - 대상은 이벤트의 `recipientIds`만 쓴다(공구·참여 테이블을 다시 읽지 않음). 비어 있으면 아무것도 하지 않는다. `GroupBuyChanged`는 `changedFields`를 쉼표로 이은 텍스트로 저장.
  - 리스너가 던지는 예외는 잡아서 로그로 남긴다(원 요청에 영향 없음). 이후 수정: 행 생성부터 try 안이라 `recipientIds`/`changedFields`가 null이어도 예외가 나가지 않는다(5종 단위 테스트).
  - 알림 내용은 제목·종류·바뀐 항목 이름·시각뿐(다른 회원 정보 금지). 이벤트에 `eventId`·`groupBuyTitle`을 추가(교차 변경).
- 설명 체크 질문:
  - 연결 후보(세션이 이 태스크에 직접 지정한 기록은 없음) 결정 1 (research.md 원문): "리스너를 `@Async`로 돌리지 않고 커밋 직후 같은 스레드에서 돌리면 FR-012에 어떤 문제가 생기는가?"
- 세션이 보낸 모범 답: 기록 없음 (SendMessage 보고는 동작 요약만: "AFTER_COMMIT 4종 → 전용 notificationExecutor, 예외는 삼킴(본 트랜잭션 영향 없음)".)
- 변이 검사·검수에서 확인한 것:
  - 독립 검수: 발행자(update·cancel·완료 경로, 005 closer)는 모두 트랜잭션 안에서 발행해 롤백·재시도 시 알림이 나가지 않음, 각 발행마다 새 UUID, 수정 이벤트에서 개설자 제외·완료 이벤트에 포함을 확인.
  - 검수가 찾은 문제(반영): 알림용 Executor 빈이 생기며 Boot 기본 실행기가 사라져 인증 메일 `@Async`가 엉뚱한 풀을 쓸 수 있었음 → `mailExecutor` 빈 명시 + `@Async("mailExecutor")`, 이후 낮음 4건에서 리스너가 `mailExecutor`에 직접 `execute`하고 큐 거부를 catch해 마스킹 수신자 WARN만 남기도록 바꾸고 `@EnableAsync` 제거. 메일 풀 거부 시 예외 대신 WARN 후 버림(요청 500 방지), core=max=2, `mail.smtps.*` 타임아웃 키도 추가. 풀이 가득 찬 상태에서 예외 없이 지나가는 테스트 + 변이(AbortPolicy) 실패 확인. 메일 풀 종료 시 대기 메일 드롭은 그대로(재전송으로 복구 가능, 낮은 심각도).
  - 변이 검사: 알림 리스너 자체의 변이로 적은 기록은 없다. 인증 메일 리스너 쪽은 검수가 코드를 읽고 try/catch를 제거하면 거부가 전파되어 테스트가 실패한다고 판단했다(실행한 변이는 아님).

### 007 T013 NotificationService 읽음 처리
- 목적: 알림 읽음 처리에서 남의 알림 존재 여부를 알려주지 않고, 이미 읽은 알림을 멱등으로 처리한다.
- 핵심 동작:
  - `UPDATE notification SET read_at = COALESCE(read_at, :now) WHERE id = :id AND recipient_id = :me` 후 영향 행이 0이면 `SELECT`로 존재·소유를 확인해 "없음 또는 남의 것"은 같은 404, "이미 읽음"은 200(소유 여부를 알려주지 않음).
  - 모든 쿼리 조건에 `recipient_id = :me`(FR-007). 목록은 키셋(`id < :cursor`), 읽지 않은 개수는 `COUNT(*) ... read_at IS NULL`. 알림함은 `JdbcClient`.
  - 읽음 처리의 마지막 SELECT는 일반 읽기로 둔다(UPDATE는 REPEATABLE READ 스냅샷을 만들지 않아 이 SELECT가 첫 일관 읽기). 처음 SendMessage 보고는 "FOR SHARE 재조회로 RR 스냅샷 stale 방지"였으나 이후 후속 항목 5)에서 일반 읽기로 바뀌었다.
- 설명 체크 질문:
  - 연결 후보(세션이 이 태스크에 직접 지정한 기록은 없음) 결정 5 (research.md 원문): "카운터 컬럼 방식이 어긋나기 쉬운 지점은 어디인가?" (읽지 않은 개수 문항이며 읽음 처리 자체에 해당하는 문항은 research.md에 없다.)
- 세션이 보낸 모범 답: 기록 없음 (SendMessage 보고는 요약만: "T013 markRead: 본인 것만, FOR SHARE 재조회로 RR 스냅샷 stale 방지".)
- 변이 검사·검수에서 확인한 것:
  - 독립 검수(후속 항목 검수): InnoDB REPEATABLE READ는 첫 비잠금 SELECT에서 read view를 만들고 UPDATE는 현재 읽기이므로 UPDATE 뒤 SELECT는 커밋된 값을 본다는 주장이 맞다고 확인(UPDATE 전 읽기 없음, auth 조회는 별도 autocommit 연결). 주석 사유("FOR SHARE는 없는 id의 갭 락만 건다")는 부정확하다는 지적(UPDATE가 이미 같은 갭 락을 건다). 동시 `markRead` 테스트는 없어 막지 않음.
  - 관제탑·독립 검수: 모든 알림함 쿼리에 `recipient_id = :me`, 남의 id와 없는 id가 같은 404임을 확인(1차 독립 검수). 변이 검사: 기록 없음.

---

## 기록 없음이 있는 태스크

(아래 스크립트로 자동 집계한 목록이다. 각 항목은 해당 필드 이름을 함께 적었다.)

- 002 T013 SignupService.signup: 세션이 보낸 모범 답
- 002 T034 LoginService.recordFailure: 세션이 보낸 모범 답
- 003 T005 DisplayStatus: 설명 체크 질문, 세션이 보낸 모범 답
- 003 T012 GroupBuyQueryService.list: 설명 체크 질문, 세션이 보낸 모범 답
- 003 T018 GroupBuyQueryService.detail: 설명 체크 질문, 세션이 보낸 모범 답, 변이 검사·검수에서 확인한 것
- 004 T007 PessimisticQuantityGate: 세션이 보낸 모범 답
- 004 T016 ConditionalUpdateQuantityGate: 설명 체크 질문
- 004 T017 OptimisticQuantityGate: 설명 체크 질문, 세션이 보낸 모범 답
- 004 T018 NoLockQuantityGate: 설명 체크 질문, 세션이 보낸 모범 답
- 004 T021 ParticipationService.leave: 세션이 보낸 모범 답
- 004 T024 ParticipationService.change: 설명 체크 질문, 세션이 보낸 모범 답
- 005 T007 GroupBuy.fail: 설명 체크 질문, 세션이 보낸 모범 답, 변이 검사·검수에서 확인한 것
- 006 T005 ParticipationRepository 쿼리: 세션이 보낸 모범 답
- 006 T006 requireCheckable: 설명 체크 질문, 세션이 보낸 모범 답
- 006 T011 setPayment: 세션이 보낸 모범 답
- 007 T007 NotificationWriter: 세션이 보낸 모범 답
- 007 T008 NotificationEventListener: 세션이 보낸 모범 답
- 007 T013 NotificationService 읽음 처리: 세션이 보낸 모범 답, 변이 검사·검수에서 확인한 것

필드 단위 합계: 29개 필드. 위 목록의 필드에는 `기록 없음`이 통째로 쓰였거나 일부 항목에 쓰였다.


---

# 파트 2. 프론트 (008)

## 먼저 알아둘 것 (사실 확인)
- `specs/008-frontend-web/tasks.md` 14행: "이 기능에는 `(핵심 로직)` 태스크가 없다". 그래서 아래는 관제탑이 예로 든 **로직 성격의 태스크**를 골랐다.
- 설명 체크 질문 원문은 `research.md` 각 결정에 있다. 관제탑 메시지의 "결정 9"는 **테스트**(Vitest+Playwright) 결정이다. 세션/401은 결정 4·10, 목록 커서는 결정 12, 시각은 결정 11이다. 결정 번호는 아래에 그대로 적었다.
- **"세션이 보낸 모범 답"**: 사용자에게 설명 체크 답을 이전에 보낸 기록은 **기록 없음**(사용자가 "설명 체크는 나중에 몰아서"라고 했고 그 뒤 보낸 적 없음). 아래 "모범 답(초안)"은 지금 구현 코드를 읽고 새로 쓴 것이다.
- "확인한 것"은 테스트로 통과 확인한 것만 적었다. 전체 기준: `npm test` 234 통과(머지 전 브랜치), `e2e` 30 통과(91db3e3 기준, d33fa96 이후 e2e 재실행 안 함). 실제 백엔드·실기기는 전부 **확인 못 함**.

---

### T007 시각 처리 `shared/lib/time.ts`
목적: 기기 시간대와 무관하게 KST로 입력·표시한다.
핵심 동작
- `formatKst`: `Intl.DateTimeFormat(timeZone:'Asia/Seoul')`로 `YYYY-MM-DD HH:mm` 표시.
- `toIsoWithKstOffset`: `datetime-local` 값을 KST로 간주해 `…+09:00`을 문자열로 붙인다(`new Date`를 거치지 않는다).
- `remainingText`: 불러온 시점(nowMs) 기준 남은 시간(분/시간/일), 실시간 갱신 없음.
- `toKstLocalInput`: 수정 폼에 현재 마감을 KST 입력값으로 채움.
설명 체크(결정 11): 기기 시간대가 UTC인 사용자가 `datetime-local`에 입력한 값을 `new Date(value)`로 보내면 무엇이 틀어지는가?
모범 답(초안): `new Date("2026-11-01T18:00")`은 기기 시간대로 해석한다. UTC 기기면 18:00 UTC가 되어 KST로는 03:00(+9h)이라 마감이 9시간 어긋나 서버에 저장된다. 그래서 문자열에 `+09:00`을 직접 붙인다.
확인한 것: `time.test.ts`가 기기 시간대를 UTC로 바꾼 상태에서 통과(`npm test`는 `TZ=UTC`로 실행). 폼 테스트(시나리오 15)에서 전송 값이 `+09:00` ISO인 것 확인. 실기기 시간대 변경은 확인 못 함.

### T011·T014 API 클라이언트와 세션/401 처리 `shared/api/client.ts`, `features/auth/session.tsx`
목적: 쿠키 세션 로그인 상태를 서버 기준으로 유지하고, 만료·로그아웃 때 개인 데이터를 화면에 남기지 않는다.
핵심 동작
- 모든 요청 `credentials:'include'`. 오류는 `ApiError{status, code, reason, fieldErrors, extra}`로 변환, 5xx·네트워크 오류는 일반 오류(서버 detail 비노출).
- 401이면 `onUnauthenticated` → 세션을 `anonymous`로 바꾸고 `queryClient.clear()`. 단 `INVALID_CREDENTIALS`(로그인 실패)와 `expect401` 호출(시작 시 `/me`, 로그아웃)은 만료로 보지 않는다.
- 앱 시작 시 `GET /api/auth/me`로 상태 확인(`checking`→`authenticated/anonymous`). 화면은 `{id, displayName}`만 메모리에 보관.
- `logout()`은 서버 성공(또는 401=이미 로그아웃) 뒤에만 캐시를 비운다. 그 외 실패는 던져서 로그인 상태를 유지하고 안내한다.
- 사용 중 만료면 `expired`가 켜져 로그인 화면이 소실 안내를 한 번만 보여준다(`ackExpired`).
설명 체크(결정 4): 로그아웃 때 쿼리 캐시를 비우지 않으면 어떤 개인 정보가 어디에 남는가? / (결정 10): 토큰을 `localStorage`에 두는 방식에 비해 `HttpOnly` 쿠키가 XSS에 안전한 이유와, 대신 CSRF를 신경 써야 하는 이유는?
모범 답(초안): 캐시 안 비우면 브라우저 메모리의 TanStack Query 캐시에 내 상세(내 참여 수량·금액, 개설자면 참여자 명단·오픈채팅 링크), 내 활동, 알림이 남아, 같은 기기에서 다음 사용자가 로그인 전 뒤로 가기 등으로 이전 사용자의 내용을 볼 수 있다. HttpOnly 쿠키는 스크립트가 읽을 수 없어 XSS로 훔칠 수 없고, localStorage 토큰은 주입된 스크립트가 바로 읽는다. 대신 브라우저가 쿠키를 자동으로 붙이므로 남이 만든 페이지의 요청에도 붙는 CSRF를 막아야 하고, 우리는 `SameSite=Lax` + 서버 `Origin` 검증으로 정했다(002 계약).
확인한 것: `session`·로그아웃·401 관련 테스트(auth.test.tsx, review.test.tsx): 로그아웃 후 캐시 비움, 로그아웃 실패 시 로그인 유지, 로그아웃 401은 만료 안내 없이 로그아웃, 만료 안내 1회. e2e 인증 만료 리다이렉트 통과. 확인 못 함: 실제 서버의 쿠키/`Origin` 검사(Vite 프록시 `changeOrigin:false`가 실제 백엔드를 통과하는지).

### T035 상세 노출 규칙 `features/groupbuy/detail/visibleActions.ts`
목적: 역할·상태에 따라 보일 버튼/영역을 한 함수로 정한다(data-model.md 3의 표).
핵심 동작
- `edit`: 개설자 + OPEN. `join`: 개설자 아님 + 참여 전 + OPEN. `changeParticipation`: 개설자 아님 + 참여함 + OPEN.
- `checkSheet`: 개설자 + COMPLETED. `myConfirm`: 개설자 아님 + 참여함 + COMPLETED.
- `openChat`·`participants`는 화면이 조건을 재판단하지 않고 서버 값이 `null`이 아닌지만 본다.
- 버튼 노출은 편의일 뿐, 최종 허용은 서버 응답(거부 문구)이 정한다.
설명 체크: tasks.md/research.md에 이 항목 전용 설명 체크 원문은 **기록 없음**. 관련 질문(제안): 화면이 버튼을 숨겨도 서버 검증이 왜 필요한가?
모범 답(초안): 버튼 숨김은 안내일 뿐 요청을 막지 못한다(직접 호출·화면이 오래된 상태·상태가 그사이 바뀜). 그래서 서버가 403/409로 거부하고, 화면은 그 코드를 문구로 보여준다. 오픈채팅·명단은 화면이 규칙을 복제하지 않고 서버가 준 값만 보여 규칙 이중 관리를 피한다.
확인한 것: `GroupBuyDetail.test.tsx`가 표를 한 행씩 검증(개설자 수량 변경 제외, 개설자는 myConfirm 없음 포함). 리뷰에서 `myConfirm`의 개설자 제외를 고침.

### T012·T048 참여 패널과 PRICE_CHANGED 흐름 `features/participation/ParticipationPanel.tsx`, `shared/lib/errorMessage.ts`
목적: 화면에서 본 가격과 서버 현재 가격이 다를 때 모르는 사이 다른 금액으로 참여하지 않게 한다.
핵심 동작
- 제출 시 화면에서 확인한 `expectedUnitPrice`를 함께 보낸다.
- 서버가 409 `PRICE_CHANGED`+`currentUnitPrice`를 주면 가격을 새 값으로 바꾸고 `needsReconfirm`을 켜 제출 버튼을 막는다. "바뀐 가격 확인했어요"를 눌러야 다시 제출 가능.
- `INSUFFICIENT_QUANTITY`(남은 수량 안내), `NOT_JOINABLE` reason 3종은 서로 다른 문구로 보이고 상세를 다시 불러온다.
- 처리 중 버튼 비활성(연타 방지), 같은 수량 재시도는 서버 200이라 오류처럼 보이지 않는다.
설명 체크: 이 흐름 전용 원문은 research.md에 **기록 없음**(결정 6 "클라이언트 검증을 통과해도 서버가 거부할 수 있는 경우는 무엇이고, 그때 입력값은 왜 지워지면 안 되는가?"가 가장 가깝다).
모범 답(초안, 결정 6 기준): 가격 변경·남은 수량 부족·마감·모집 종료는 화면이 아는 시점 이후에 서버 상태가 바뀐 경우라 클라이언트 검증을 통과해도 거부된다. 입력값(수량)을 지우면 사용자가 처음부터 다시 쳐야 하고 어떤 값이 거부됐는지 비교도 못 한다. 그래서 값은 유지하고 서버 필드 오류/안내를 옆에 보인다.
확인한 것: `Participation.test.tsx`(시나리오 21~26), `messages.test.ts`(참여 관련 행이 서로 구분되는 문구), 목 핸들러 기준. 실제 백엔드 연동은 확인 못 함.

### T019·T021·T022 목록 조건과 커서 `features/groupbuy/list/listParams.ts`, `useGroupBuyList.ts`
목적: 목록 조건을 주소 쿼리에 두고, 조건이 바뀌면 커서를 버리고 처음부터 불러온다.
핵심 동작
- 주소 쿼리 `status/category/q/sort` ↔ 조건 변환. 알 수 없는 값은 기본값(`status=OPEN`, `sort=latest`)으로 되돌림, `q`는 trim·50자·공백뿐이면 검색 안 함. 커서는 주소에 넣지 않는다.
- `useInfiniteQuery` 키 `['groupbuys', 조건]`: 조건이 다르면 키가 달라 이전 `nextCursor`를 쓰지 않는다. `getNextPageParam = nextCursor`, null이면 더 보기 사라짐.
- 검색어는 300ms 디바운스 후 주소에 반영(`replace`), 타이머가 오래된 params로 덮어쓰지 않게 최신 값을 ref로 읽는다.
설명 체크(결정 3): 목록 조건을 컴포넌트 상태가 아니라 주소 쿼리에 두면 새로고침·공유·뒤로 가기에서 무엇이 달라지는가? / (결정 12): 조건이 바뀌었는데 이전 `nextCursor`를 그대로 쓰면 서버는 어떻게 응답하는가(003 계약)?
모범 답(초안): 주소에 두면 새로고침해도 조건이 유지되고, 링크를 공유하면 같은 목록이 열리며, 뒤로 가기에서 조건이 복원된다. 컴포넌트 상태면 셋 다 기본값으로 초기화된다. 커서는 조건과 묶인 불투명 값이라 조건이 바뀐 뒤 옛 커서를 쓰면 003 계약상 서버가 거부한다(정확한 오류 코드는 003 계약 원문으로 확인 필요 — 이 세션은 **코드 기록 없음**). 그래서 키에 조건을 넣어 커서를 버린다.
확인한 것: `listParams.test.ts`, `GroupBuyList.test.tsx`(시나리오 2·3), e2e list.spec(새로고침 후 조건 유지). 상세에서 돌아올 때 스크롤 위치 복원(미검증 가정 4)은 e2e로 **확인 못 함**.

### T056·T057 체크표 토글 `features/check/`
목적: 개설자가 참여자별 입금·수령을 켜고 끄고, 요약을 항상 전체 기준으로 보인다.
핵심 동작
- 쿼리 키 `['check', id, filter]`. 필터는 목록만 거르고 요약은 서버가 항상 전체 기준으로 줌.
- 토글은 PUT(`{confirmed}`, 멱등). 성공하면 `['check', id]`를 무효화해 모든 필터와 요약을 다시 조회.
- 처리 중 해당 행 버튼 비활성(연타 방지), 입금 없이 수령된 행은 따로 표시.
- 거부(`ORGANIZER_ONLY`/`NOT_CHECKABLE`)는 안내 + 상세로, 실패 시 상세 쿼리도 무효화. 체크는 알림을 보내지 않는다.
설명 체크: 체크표 전용 원문은 research.md에 **기록 없음**.
모범 답(초안): 낙관적 갱신 대신 서버 응답 후 재조회로 요약과 행이 항상 서버 기준으로 일치한다. PUT이 멱등이라 연타·재시도로 상태가 어긋나지 않는다.
확인한 것: `CheckSheet.test.tsx`(필터 시 요약 유지, 토글·되돌리기, 연타 1회, 거부 안내, 알림 쓰기 요청 없음). 브라우저(e2e)에서 /check 흐름은 **확인 못 함**.

### 결정 5 (API 타입)·결정 8·9·13 중 코드 근거가 있는 답
- 결정 5 "서버가 모르는 status를 주면 화면은 어디서 어떻게 막아야 하는가?" 모범 답(초안): API 경계의 타입 가드(`isKnownStatus`)와 뱃지/카드 표시 한 곳에서 '알 수 없음'으로 처리한다. 확인: `Badge`가 알 수 없는 값은 "알 수 없음", 목록 테스트 시나리오 6.
- 결정 9 "화면 테스트가 통과해도 실제 백엔드와 안 맞을 수 있는 이유와 줄이는 방법은?" 모범 답(초안): 테스트가 쓰는 건 MSW 목이라 목이 계약에서 벗어나면 같이 통과한다. 줄이는 방법은 핸들러를 `ui-api-map.md`와 계약에 대조(T066 표)하고 백엔드 구현 시 quickstart를 `VITE_USE_MOCK` 없이 다시 돌리는 것. 확인 못 한 것: 실제 백엔드로 돌린 결과(전부).
- 결정 8·13·14는 이 정리의 로직 항목이 아니라서 생략(원문 질문은 research.md 참고).

## 전체 "확인 못 한 것"
실제 백엔드 연동(Origin 검사 포함), 실기기·PWA 설치·standalone, 실기기 포커스, 첫 화면 로딩(SC-006), `/me`·`/check`·`/notifications`의 e2e, d33fa96 이후 e2e 재실행, 동의문 `v1` 초안 확정.
