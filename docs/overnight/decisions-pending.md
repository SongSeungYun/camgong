# 결정 현황

**2026-10-26 사용자가 '추천대로' 확정**: 아래 1절(006·007·002 research 결정 30개), 2절 #6(005는 스케줄러 트랜잭션 READ COMMITTED) · #7(직접 UPDATE에 `version = version + 1`), 3절 002 spec 보완 제안 5건은 추천안으로 확정됐다. spec/기존 문서에 실제로 반영하는 작업은 아직 안 했다. **추천이 명확하지 않아 남은 것**: 2절 #1~#5(004 멱등 vs FR-009 규칙 순서, 오류 코드 체계 Y3, 008 범위, 거부 문구, 용어), 사용자 준비물(메일 SMTP 계정, 학교 도메인 값), 단계 0·설명 체크.

(아래는 밤샘 작성 원문)

밤샘 동안 사용자 결정이 필요해서 **확정하지 않고 남긴 것** 모음이다. 각 항목의 후보·추천·설명 체크는 링크된 research에 있다. 모두 `상태: 사용자 결정 대기`.

## 1. 기능별 plan 결정 (research.md)

| 기능 | 문서 | 결정 수 | 비고 |
|------|------|---------|------|
| 006 개설자 체크 | [research.md](../../specs/006-organizer-check/research.md) | 8 | 체크 API 모양, 조건부 UPDATE, 에러 코드 `ORGANIZER_ONLY`/`NOT_CHECKABLE` |
| 007 알림 | [research.md](../../specs/007-notify/research.md) | 10 | 이벤트에 `eventId`·`groupBuyTitle` 추가(교차 변경), AFTER_COMMIT+@Async, 배치 INSERT |
| 002 로그인·가입 | [research.md](../../specs/002-login-signup/research.md) | 12 | 새 의존성(Spring Security, starter-mail), 메일 계정·학교 도메인은 사용자 준비물 |

## 2. 기존 spec·문서에 걸린 결정

| # | 내용 | 출처 | 추천 |
|---|------|------|------|
| 1 | 004 같은 수량 재시도(멱등)와 모집 완료 후 거부(FR-009)의 충돌 | [analyze-004.md](analyze-004.md) B1 | **확정(2026-10-27, A)**: 이미 참여한 사람의 같은 수량 재시도는 공구 상태와 무관하게 성공(현재 상태 반환). 모집 중이 아닌 공구의 첫 참여는 거부. 실패(`FAILED`) 공구의 옛 참여 재시도는 409, 취소는 404. spec/tasks 반영은 대기 |
| 2 | 오류 코드 체계 통일 | [cross-check-2.md](../cross-check-2.md) Y3 | **확정(2026-10-27, A)**: 코드 이름은 유지, 상태 때문에 거부될 때 응답에 `reason` 필드(예: `NOT_JOINABLE` → `DEADLINE_PASSED`/`COMPLETED`/`FAILED`, `NOT_MODIFIABLE`도 동일). `docs/architecture.md` 6.1에 전체 오류 코드 표를 모음(반영 대기). 002·006 contracts의 코드 이름도 그 표에서 확정 |
| 3 | 008 프론트 spec의 범위가 낡음 | cross-check-2 Y2 | **확정(2026-10-27, A)**: 내가 수정안 문서(정확한 문구)를 `docs/spec-change-proposals.md`에 쓰고, 사용자가 읽고 반영 여부 결정(작성 대기) |
| 4 | 004 거부 문구 불일치 | cross-check-2 Y1 | **확정(2026-10-27, A)**: 403 `ORGANIZER_NOT_ALLOWED`, 안내 "개설자는 직접 참여할 수 없어요. 수량은 공구 수정에서 바꿔요." 004 시나리오 5를 FR-010에 맞춤(반영 대기) |
| 5 | 용어집 ↔ 003 `참여 인원 수` | cross-check-2 Y7 | **확정(2026-10-27, A)**: 용어집에 `참여 인원 수`=`participantCount`(사람 수, 수량 합 아님)를 정식 용어로 추가, 금지어는 "수량 합을 가리킬 때는 쓰지 않음"으로 한정 |
| 6 | **005 SKIP LOCKED 워커 동시 처리**: REPEATABLE READ에서 `status` 갱신 UPDATE가 락 대기 시간 초과(스파이크 재현). 후보 A 스케줄러 트랜잭션 READ COMMITTED / B id만 뽑고 건별 짧게 처리 / C 단일 워커 | [spikes.md](../spikes.md) 'Spring 수준 검증' | A (상태: 사용자 결정 대기) |
| 7 | 004 직접 UPDATE는 `version = version + 1` 포함 규칙 명시 | spikes.md | 규칙 추가 |

## 3. 002 spec 보완 제안 (spec은 수정하지 않았음)

[002 research.md](../../specs/002-login-signup/research.md) 상단 목록 참고:

1. 비밀번호 최대 길이(BCrypt 72바이트) 명시
2. 잠금 안내와 계정 존재 숨김의 모순 해소(이메일 키 카운트)
3. 인증 링크는 화면 → `POST`로 확인
4. 테스트 인증 헬퍼 `as(memberId)`를 001부터 도입
5. 허용 도메인 값, 메일 발송 계정 준비

## 4. 사용자만 할 수 있는 것

- 단계 0 수요 확인, 설명 체크(질문 17개), README TODO, 설계 소요 시간 기록
- `overnight/2026-10-26` 브랜치를 main에 합칠지, push할지
- 추천안을 그대로 받아들이면 "추천대로"라고만 말해도 된다. 바꾸고 싶은 항목만 번호로 알려줘.
