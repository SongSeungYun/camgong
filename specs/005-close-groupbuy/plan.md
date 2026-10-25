# Implementation Plan: 마감 확정(모집 실패 처리)

**Branch**: `005-close-groupbuy` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-close-groupbuy/spec.md`

## Summary

마감 시각이 지났는데 수량을 못 채운 공구를 백그라운드에서 자동으로 모집 실패로 확정하고, 개설자를 포함한 참여자 전원 대상 이벤트를 한 번 발행한다.
사용자 화면은 없다. 서버가 여러 대여도 같은 공구는 한 번만 확정되어야 한다(헌법 IV 필수 테스트).

핵심 설계 결정(사용자 결정, 근거는 [research.md](research.md)):

- 중복 방지는 **행 락 + `SKIP LOCKED`**: 공구 1건씩 `SELECT ... FOR UPDATE SKIP LOCKED`로 잡고, 이미 다른 곳이 잡았거나 확정한 공구는 건너뛴다.
- 실행 주기는 **30초**(`@Scheduled(fixedDelay)`).
- 트랜잭션 단위는 **공구 1건씩**: 한 건의 실패가 다른 건을 막지 않는다.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: 001·004 plan과 같다. 새 의존성 없음. 스케줄링은 Spring 내장 `@Scheduled`

**Storage**: MySQL 8.4. 새 테이블 없음. `group_buy`에 상태 값 `FAILED`와 인덱스 `(status, deadline)`을 더한다

**Testing**: JUnit 5, Testcontainers(MySQL). 여러 인스턴스는 같은 DB에 동시에 확정 서비스를 호출하는 여러 스레드로 모사한다

**Target Platform**: 로컬 Docker. 실제 서버 여러 대 구성은 나중이지만 요구사항으로 검증한다

**Project Type**: web-service 내부 백그라운드 작업 (화면 없음)

**Performance Goals**: SC-001 마감 후 1분 이내 95% 확정. SC-007 1,000건이 같은 시각에 마감돼도 10분 이내에 모두 확정하고 그동안 목록·상세 조회 95%가 1초 이내

**Constraints**: FR-007 중복 확정·중복 이벤트 0건. FR-008 한 건의 실패가 다른 건을 막지 않음. FR-009·010 004·001과 직렬화, 마감 전 확정 금지

**Scale/Scope**: 한 번에 마감되는 공구 최대 약 1,000건

## Constitution Check

*GATE: Phase 0 전 통과 필요. Phase 1 설계 후 재확인.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. 범위 상한 | 통과 | 화면 없는 백그라운드 처리 하나. 재모집·알림 전달은 범위 밖 |
| II. 측정으로 증명 | 통과 (해당 없음) | 쿼리 수·동시성 비교 대상이 아니다. 정확성은 통합 테스트로, 지연·처리량은 quickstart의 측정 시나리오로 기록한다 |
| III. 설명 가능한 설계 | 통과 | 사용자 결정 3개와 기본값에 대안·이유·설명 체크 문항이 있다 |
| IV. 핵심 로직 테스트 | 통과 | **스케줄러 중복 실행 방지**(필수), 모집 완료/실패 전환 경쟁, 마감 확정을 Testcontainers(MySQL) 통합 테스트로 덮는다 |

**설계 후 재확인 (Phase 1)**: 위 판정 그대로 통과. 새 외부 의존성과 불필요한 추상화는 없다.

## Project Structure

### Documentation (this feature)

```text
specs/005-close-groupbuy/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── events-and-config.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### Source Code (repository root)

```text
src/main/java/com/camgong/groupbuy/
├── CloseExpiredScheduler.java        # @Scheduled(fixedDelay=30s) → 서비스 호출, 설정으로 끌 수 있음
├── GroupBuyCloseService.java         # 후보 조회 → 건별 확정 호출 라운드 반복, 통계·로그
├── GroupBuyCloser.java               # 공구 1건의 확정 트랜잭션 (별도 빈, SKIP LOCKED로 선점)
├── CloseStats.java                   # 한 실행의 결과(closed, skipped, failed)
├── GroupBuy.java                     # (수정) fail() 상태 전이 메서드 추가
├── GroupBuyStatus.java               # (수정) FAILED 추가
├── GroupBuyRepository.java           # (수정) 후보 id 조회, SKIP LOCKED 선점 쿼리
├── GroupBuyEvents.java               # (수정) GroupBuyFailed record 추가
└── GroupBuyEventLogger.java          # (수정) GroupBuyFailed 로그
src/main/resources/
├── schema.sql                        # (수정) 인덱스 idx_group_buy_status_deadline
└── application.yml                   # (수정) camgong.scheduler.close.* 설정
src/test/java/com/camgong/groupbuy/
├── CloseExpiredIT.java               # 대상 선별, 확정 결과, 이벤트 1회
├── CloseConcurrencyIT.java           # 여러 인스턴스 모사, 중복 확정·이벤트 0건
├── CloseRaceIT.java                  # 004 참여·001 수정/취소와의 마감 경계 경쟁
├── CloseResilienceIT.java            # 일부 실패 격리, 재시도, 재시작 후 밀린 공구
└── CloseSchedulerWiringTest.java     # 스케줄러가 서비스를 주기적으로 호출
```

**Structure Decision**: 확정 서비스(`GroupBuyCloseService`)와 건별 확정(`GroupBuyCloser`)을 별도 빈으로 나눈다. 건별 트랜잭션이 프록시를 거쳐야 실제로 건마다 시작되기 때문이다. 스케줄러는 서비스를 부르기만 하는 얇은 껍데기라 테스트는 서비스를 직접 호출한다.

## Complexity Tracking

헌법 위반 없음.
