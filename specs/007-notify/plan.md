# Implementation Plan: 알림

**Branch**: `007-notify` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-notify/spec.md`

> **초안 (밤샘 작성, 사용자 결정 대기)**: 이 plan의 설계 결정은 모두 [research.md](research.md)의 **추천안 기준**으로 쓴 가안이다. 사용자가 결정하기 전에는 확정이 아니다. 결정 목록은 [docs/overnight/decisions-pending.md](../../docs/overnight/decisions-pending.md).

## Summary

모집 완료·모집 실패·수정·취소 사건이 일어나면 받을 회원의 앱 안 알림함에 알림을 쌓고, 회원이 목록·읽지 않은 개수·읽음 처리를 쓸 수 있게 한다. 이메일·푸시는 없다.

추천안의 골자:

- 알림은 001·004·005가 이미 발행하는 이벤트(`recipientIds` 포함)를 **커밋 후 비동기로** 받아 만든다. 원래 요청은 알림 때문에 느려지지 않는다(FR-012).
- 중복 방지는 **이벤트마다 고유 `eventId`**와 `UNIQUE (event_id, recipient_id)`다. 같은 이벤트가 두 번 처리돼도 한 번만 들어간다(FR-005).
- 대량 생성은 **JDBC 배치 삽입**이고 청크가 실패하면 단건으로 격리해 한 사람의 실패가 다른 사람을 막지 않는다(FR-009).
- 30일 지난 알림은 005와 같은 스케줄러 패턴으로 정리한다.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: 001 plan과 같다. 새 의존성 없음. 비동기는 Spring `@Async`

**Storage**: MySQL 8.4. 새 테이블 `notification` 1개 ([data-model.md](data-model.md))

**Testing**: JUnit 5, Testcontainers(MySQL)

**Target Platform**: 로컬 Docker

**Project Type**: web-service (읽기 API + 이벤트 소비)

**Performance Goals**: SC-001 사건 후 95%가 1분 이내 알림 생성, SC-005 참여자 1,000명 사건 한 건이 5분 이내 완료, SC-006 알림 목록 95%가 1초 이내

**Constraints**: FR-005 중복 없음, FR-008 롤백된 변경은 알림 없음, FR-009 실패 격리·재시도, FR-012 원 요청에 영향 없음. 이벤트 유실은 허용된 한계(004·005와 같음)

**Scale/Scope**: 회원당 알림은 30일 보관이라 수십~수백 건, 사건 한 건의 수신자 최대 약 1,000명

## Constitution Check

*GATE: Phase 0 전 통과 필요. Phase 1 설계 후 재확인.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. 범위 상한 | 통과 | 앱 안 알림함 하나. 이메일·푸시·알림 끄기·마감 임박 알림은 넣지 않았다 |
| II. 측정으로 증명 | 통과 (해당 없음) | 비교 측정 대상이 아니다. 대량 생성 시간은 기록용으로 잰다 |
| III. 설명 가능한 설계 | 통과 (결정 대기) | 결정마다 후보·추천·설명 체크가 있다. 사용자 결정이 필요하다 |
| IV. 핵심 로직 테스트 | 통과 (해당 없음) | 필수 대상이 없다. 중복 방지·실패 격리·수신자 정확도는 통합 테스트로 덮는다 |

## Project Structure

### Documentation (this feature)

```text
specs/007-notify/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── notification-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성 (구현 직전)
```

### Source Code (repository root)

```text
src/main/java/com/camgong/notification/            # 새 최상위 패키지 (architecture 5절의 notification/)
├── NotificationController.java         # 목록, 읽지 않은 개수, 읽음 처리
├── NotificationService.java            # 조회·읽음 처리
├── NotificationEventListener.java      # AFTER_COMMIT + @Async로 이벤트를 받아 생성 호출
├── NotificationWriter.java             # 배치 삽입, 중복 무시, 청크 실패 시 단건 격리, 재시도
├── NotificationView.java               # 응답 record
├── NotificationCleanup.java            # 30일 지난 알림 정리 (@Scheduled)
└── AsyncConfig.java                    # 알림 전용 실행기(작은 풀, 큐 상한)
src/main/java/com/camgong/groupbuy/                 # (수정) 이벤트에 eventId 추가
└── GroupBuyEvents.java                 # 네 이벤트 record에 eventId(UUID)
src/main/resources/
├── schema.sql                          # (수정) notification 테이블
└── application.yml                     # (수정) camgong.notification.* 설정
src/test/java/com/camgong/notification/
├── NotificationCreateIT.java           # 사건 4종 → 대상·내용
├── NotificationDedupIT.java            # 같은 사건 중복 처리, 동시 처리
├── NotificationInboxIT.java            # 목록·읽지 않은 개수·읽음·타인 알림·401
├── NotificationResilienceIT.java       # 한 사람 실패 격리, 재시도, 대량(1,000명)
└── NotificationCleanupIT.java          # 30일 정리
```

**Structure Decision**: 알림은 공구 애그리거트의 일부가 아니라 다른 기능이 내보낸 이벤트를 소비하는 독립 기능이라 최상위 패키지 `notification`을 둔다. 의존 방향은 `notification → groupbuy`(이벤트 읽기)뿐이고 반대 방향 의존은 없다.

## Complexity Tracking

헌법 위반 없음. 이벤트 record에 `eventId`를 더하는 것은 001·004·005 코드를 건드리는 교차 변경이라 research.md 결정 2에 영향 범위를 적었다.
