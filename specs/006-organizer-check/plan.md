# Implementation Plan: 개설자용 참여자·입금·수령 체크

**Branch**: `006-organizer-check` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-organizer-check/spec.md`

> **초안 (밤샘 작성, 사용자 결정 대기)**: 이 plan의 설계 결정은 모두 [research.md](research.md)의 **추천안 기준**으로 쓴 가안이다. 사용자가 결정하기 전에는 확정이 아니다. 결정이 바뀌면 이 문서도 함께 고친다. 결정 목록은 [docs/overnight/decisions-pending.md](../../docs/overnight/decisions-pending.md).

## Summary

모집이 완료된 공구에서 개설자가 참여자별 입금·수령을 체크하고 되돌린다. 참여자는 자기 확인 상태를 상세에서 본다. 돈은 서비스 밖에서 오가며 체크는 개설자의 기록일 뿐이다.

추천안의 골자:

- 체크는 **필드별 `PUT`**(입금, 수령 각각)으로 **목표 상태를 지정**한다. 같은 요청을 반복해도 결과가 같다.
- 쓰기는 `participation` 한 행에 대한 **조건부 `UPDATE` 한 문장**이다. 공구 행 락은 필요 없다(`COMPLETED`는 종료 상태라 바뀌지 않는다).
- 입금·수령 확인 시각은 `participation`의 두 컬럼(`paid_at`, `received_at`)이다. 값이 없으면 확인 전이다.
- 체크표 조회는 003의 명단 쿼리를 재사용한다(N+1 점검 대상을 하나로).

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
