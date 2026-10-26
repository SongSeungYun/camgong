# spec 변경 제안 (사용자 반영 대기)

2026-10-27 결정을 `spec.md`에 반영하는 정확한 문구다. 기존 spec은 수정하지 않았다. 반영하려면 해당 문구를 붙여넣거나 "반영해"라고 하면 된다(`specs/*/spec.md`는 권한 deny 규칙이 있어 규칙을 풀어줘야 내가 직접 고칠 수 있다).

## 004 participate-groupbuy

1. **FR-004 보강**: "이미 참여한 상태에서 같은 수량으로 다시 요청하면 공구가 이후 모집 완료가 되었더라도 성공으로 현재 상태를 돌려준다. 공구가 모집 실패(`FAILED`)이면 옛 참여라도 거부(409 `NOT_JOINABLE`, `reason: FAILED`)하고, 취소된 공구는 404다."
2. **US1 시나리오 5**: "개설자가 자기 공구에 참여하려 하면 거부하고 '개설자는 직접 참여할 수 없어요. 수량은 공구 수정에서 바꿔요.'라고 안내한다(403 `ORGANIZER_NOT_ALLOWED`)." (FR-010과 같은 문구로 통일)
3. **오류 본문**: `NOT_JOINABLE`은 `reason`(`DEADLINE_PASSED` / `COMPLETED` / `FAILED`)을 포함한다. (`contracts/participation-api.md`, `docs/architecture.md` 6.1에 반영됨)

## 001 create-groupbuy (contracts)

- `NOT_MODIFIABLE` 응답에 `reason`(`COMPLETED` / `DEADLINE_PASSED`)을 포함한다.

## 002 login-signup (spec 보완 5건, 2026-10-26 추천대로 확정)

1. 비밀번호는 8자 이상 **72바이트 이하**.
2. 로그인 실패 횟수는 이메일 키로 센다. 잠금 안내는 계정 존재 여부와 무관하게 같다(FR-010·011 모순 해소).
3. 인증 링크는 화면을 열고 화면이 확인 요청(`POST`)을 보낸다.
4. 테스트는 `as(memberId)` 헬퍼로 인증한다(001부터 도입).
5. 허용 학교 이메일 도메인과 메일 발송 계정은 설정 값으로 주입한다(사용자 준비물).

## 008 frontend-web (Y2)

1. **Input / 범위**: "기존 기능 001~005"를 "001~007"로 바꾸고, 002·006·007 계약(`specs/002-login-signup/contracts/auth-api.md`, `specs/006-organizer-check/contracts/check-api.md`, `specs/007-notify/contracts/notification-api.md`)을 근거에 추가한다.
2. **S09 (입금·수령 체크)**: "006 spec이 생기면 추가"를 삭제하고 006 기준 화면(체크표, 필터 전체/미입금/미수령, 진행 요약)을 범위에 넣는다.
3. **S10 (알림)**: "전달 수단이 정해지면 추가"를 삭제하고 "앱 안 알림함(목록, 읽지 않은 수, 읽음 처리)"으로 정한다.
4. **FR-028 거부 사유 표시**: `NOT_JOINABLE` 응답의 `reason`으로 '마감'과 '이미 끝난 모집'을 구분해 보여준다(상세 재요청 불필요).

## 003 browse-groupbuy

- 변경 없음. 용어집에 `참여 인원 수`(`participantCount`)를 정식 용어로 추가했다(`docs/domain.md`).
