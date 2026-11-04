# Implementation Plan: 개설자용 참여자·입금·수령 체크

**Branch**: `006-organizer-check` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-organizer-check/spec.md`

> **추천안으로 확정 (2026-10-26)**: 이 plan의 설계 결정은 모두 [research.md](research.md)의 **추천안 기준**으로 쓴 가안이다. 2026-10-26 사용자가 추천대로 확정했다(준비물·오류 코드 이름 등 남은 항목은 decisions-pending.md). 결정이 바뀌면 이 문서도 함께 고친다. 결정 목록은 [docs/overnight/decisions-pending.md](../../docs/overnight/decisions-pending.md).

## Summary

모집이 완료된 공구에서 개설자가 참여자별 입금·수령을 체크하고 되돌린다. 참여자는 자기 확인 상태를 상세에서 본다. 돈은 서비스 밖에서 오가며 체크는 개설자의 기록일 뿐이다.

추천안의 골자:

- 체크는 **필드별 `PUT`**(입금, 수령 각각)으로 **목표 상태를 지정**한다. 같은 요청을 반복해도 결과가 같다.
- 쓰기는 `participation` 한 행에 대한 **조건부 `UPDATE` 한 문장**이다. 공구 행 락은 필요 없다(`COMPLETED`는 종료 상태라 바뀌지 않는다).
- 입금·수령 확인 시각은 `participation`의 두 컬럼(`paid_at`, `received_at`)이다. 값이 없으면 확인 전이다.
- 체크표 조회는 003의 명단 쿼리를 재사용한다(N+1 점검 대상을 하나로). → 구현에서는 같은 방식의 전용 쿼리로 바뀌었다(아래 "구현 반영").

## 구현 반영 (2026-11-05, 코드 대조)

main의 구현(`src/main/java/com/camgong/groupbuy/check/`)과 이 plan이 다른 점. 계약·동작은 바뀌지 않았고 구현 방식만 다르다.

- **체크표 조회는 003 명단 쿼리를 재사용하지 않았다.** `CheckQueryRepository`를 새로 두고 003과 같은 방식(필요한 컬럼만 조인하는 DTO 프로젝션, 지연 로딩 없음)으로 쿼리 4개(`findAll`·`findUnpaid`·`findUnreceived`·`findOne`)를 따로 썼다. 필터를 서버 쿼리 조건으로 처리하려고 나눴다. 요약은 필터와 무관하게 `participation` 집계 SQL 한 문장(개설자 제외)으로 구한다. 쿼리 수가 참여자 수와 무관함은 `CheckSheetQueryCountIT`(JDBC 수준 계수)로 확인한다.
- **쓰기는 조건부 `UPDATE` 한 문장 그대로**이고, 개설자 제외 조건(`member_id <> (SELECT organizer_id …)`)이 문장 안에 있다. 입금·수령 × 지정·해제를 SQL 4개로 따로 뒀다(컬럼명을 인자로 조립하지 않음).
- **응답 행은 `UPDATE` 뒤 `FOR SHARE`로 다시 읽는다.** 동시 요청이 먼저 같은 상태로 바꿔 내 `UPDATE`가 아무것도 바꾸지 못하면 일반 `SELECT`는 트랜잭션 스냅샷의 낡은 값을 돌려주기 때문이다(`CheckStaleResponseIT`).
- **참여 행 존재 확인을 `UPDATE` 앞에서 따로 한다.** `useAffectedRows=true`에서 값이 안 바뀐 `UPDATE`는 영향 행 0이라 "없음"과 "이미 그 상태"를 영향 행 수로 구분할 수 없다(미검증 가정 1의 결론). 없으면 404 `PARTICIPATION_NOT_FOUND`.
- 접근 검사는 004의 `JoinQueryRepository.findHead`를 재사용한다.
- 테스트 파일이 위 구조보다 많다: `CheckSheetQueryCountIT`, `CheckStaleResponseIT`, `MyConfirmStatusIT`(참여자 본인 확인 상태), 006·007 후속에서 `CompletedImmutableIT`가 더해졌다.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: 001 plan과 같다. 새 의존성 없음

**Storage**: MySQL 8.4. 새 테이블 없음. `participation`에 컬럼 2개 추가

**Testing**: JUnit 5, Testcontainers(MySQL), 003의 `QueryCountInspector`

**Target Platform**: 로컬 Docker

**Project Type**: web-service

**Performance Goals**: SC-002 참여자 50명 체크표 95%가 1초 이내

**Constraints**: FR-005 멱등·동시 요청에도 어긋나지 않음, FR-001 개설자·모집 완료 공구만, 체크표 쿼리 수는 참여자 수와 무관

**Scale/Scope**: 공구당 참여자 최대 약 1,000명(수량 1개씩), 체크는 개설자 한 명이 하는 저빈도 쓰기

## Constitution Check

*GATE: Phase 0 전 통과 필요. Phase 1 설계 후 재확인.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. 범위 상한 | 통과 | MVP에 들어 있는 "개설자용 참여자·입금·수령 체크"다. 일괄 체크·이력·참여자 보고·알림은 넣지 않았다 |
| II. 측정으로 증명 | 통과 (해당 없음) | 비교 측정 대상이 아니다. 체크표 쿼리 수 상한은 003의 회귀 테스트 방식을 따른다 |
| III. 설명 가능한 설계 | 통과 (결정 대기) | 결정마다 후보·추천·설명 체크를 research.md에 적었다. 사용자가 결정해야 확정된다 |
| IV. 핵심 로직 테스트 | 통과 (해당 없음) | 필수 대상이 없다. 동시 체크와 접근 규칙은 통합 테스트로 덮는다 |

## Project Structure

### Documentation (this feature)

```text
specs/006-organizer-check/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── check-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성 (구현 직전)
```

### Source Code (repository root)

```text
src/main/java/com/camgong/groupbuy/check/          # 새 하위 패키지
├── ParticipationCheckController.java   # 체크표 조회, 입금·수령 PUT
├── ParticipationCheckService.java      # 접근 규칙(개설자·모집 완료), 조건부 UPDATE 호출
├── CheckSheet.java                     # 체크표 응답(행 목록 + 요약)
├── CheckRow.java                       # 참여자 한 명의 행
└── CheckRequest.java                   # { confirmed }
src/main/java/com/camgong/groupbuy/                 # (수정)
├── Participation.java                  # paidAt, receivedAt 추가
└── ParticipationRepository.java        # 체크 UPDATE, 개설자 제외 명단 조회
src/main/java/com/camgong/groupbuy/query/            # (수정, 003 구현 이후)
└── GroupBuyDetail.java                 # myParticipation에 확인 상태 추가
src/main/resources/
└── schema.sql                          # (수정) participation 컬럼 2개
src/test/java/com/camgong/groupbuy/check/
├── CheckSheetIT.java                   # 체크표 내용·요약·필터·개설자 행 제외
├── CheckConfirmIT.java                 # 입금·수령 지정, 되돌리기, 독립성, 멱등
├── CheckAccessIT.java                  # 개설자만, 모집 완료만, 404·400
└── CheckConcurrencyIT.java             # 같은 참여자 동시 체크
```

**Structure Decision**: 기능별 하위 패키지 규칙(`groupbuy.query/join/close`)에 맞춰 `groupbuy.check`를 둔다. 애그리거트 필드 추가는 `groupbuy` 루트에서 한다.

## Complexity Tracking

헌법 위반 없음.
