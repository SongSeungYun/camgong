# Implementation Plan: 공구 참여·수량 변경·참여 취소

**Branch**: `004-participate-groupbuy` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-participate-groupbuy/spec.md`

## Summary

로그인 회원이 모집 중 공구에 수량을 정해 참여하고, 수량을 바꾸고, 취소한다. 확보 수량이 총 수량에 닿는 순간 모집이 끝난다.
이 기능은 헌법 II의 측정 대상이라 **마지막 남은 수량 경쟁을 4가지 방식(락 없음, 비관적, 낙관적, 조건부 UPDATE)으로 구현해 k6로 비교**한다.

핵심 설계 결정(사용자 결정, 근거는 [research.md](research.md)):

- 경쟁 지점은 `QuantityGate` 인터페이스 하나이고 4개 구현체를 **설정 값으로 고른다**. 구현체가 넷이라 인터페이스가 정당하다.
- 참여 API는 **리소스형**이다: `POST /api/groupbuys/{id}/participations`, `PUT`·`DELETE .../participations/me`.
- 마감 판정 시각은 **앱 `Clock`**이다. 마감 확정(005)만 마감 시각 + 1초 이후에 처리해 시계 오차를 막는다.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: 001 plan과 같다(Spring Web, Data JPA, Validation, MySQL 드라이버). 새 의존성 없음. 부하 측정은 k6(헌법 II)

**Storage**: MySQL 8.4. 새 테이블 없음. `participation`과 `group_buy`는 001이 만든 것을 쓴다

**Testing**: JUnit 5, Testcontainers(MySQL). 동시성 정확성은 스레드 기반 통합 테스트(SC-002의 1,000회 반복), 성능은 k6

**Target Platform**: 로컬 Docker(측정은 [research.md](research.md) 결정 8의 고정 환경)

**Project Type**: web-service (001과 같은 단일 모듈)

**Performance Goals**: SC-005 — 100명 동시 요청에서 95%가 1초 이내 응답. 4방식 비교 수치는 `docs/benchmarks/`에 기록

**Constraints**: FR-006 확보 수량 ≤ 총 수량, 모집 완료 전환 정확히 한 번. FR-011 001 수정·취소, 005 확정과 같은 공구 행에서 직렬화

**Scale/Scope**: 공구당 수십 명 이하 참여, 동시 요청 최대 100

## Constitution Check

*GATE: Phase 0 전 통과 필요. Phase 1 설계 후 재확인.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. 범위 상한 | 통과 | MVP 목록의 참여/취소다. 대기열, 1인당 상한, 알림 전달은 넣지 않았다 |
| II. 측정으로 증명 | 통과 | 4방식 구현과 k6 시나리오, 지표, 고정 환경을 research.md 결정 8에서 확정했다. 결과는 `docs/benchmarks/`에 남긴다 |
| III. 설명 가능한 설계 | 통과 | 결정 3개는 사용자 결정, 나머지 기본값은 research.md에 대안과 이유가 있고 설명 체크 문항이 달려 있다 |
| IV. 핵심 로직 테스트 | 통과 | 수량 초과 판정, 모집 완료 전환, 동시 참여 경쟁을 Testcontainers(MySQL) 통합 테스트로 덮는다 |

**설계 후 재확인 (Phase 1)**: 위 판정 그대로 통과. 인터페이스 `QuantityGate`는 구현체 4개라 "구현체가 하나뿐이면 만들지 않는다"는 규칙에 걸리지 않는다.

## Project Structure

### Documentation (this feature)

```text
specs/004-participate-groupbuy/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── participation-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### Source Code (repository root)

001이 만든 `groupbuy` 패키지에 더한다. `Participation`, `ParticipationRepository`는 001에서 이미 있다.

```text
src/main/java/com/camgong/groupbuy/
├── ParticipationController.java
├── ParticipationService.java            # 흐름 조율: 멱등 확인, 게이트 호출, 참여 행 변경, 이벤트
├── ParticipationRequest.java            # quantity, expectedUnitPrice
├── ParticipationResponse.java
├── QuantityGate.java                    # 경쟁 지점 인터페이스
├── GateResult.java                      # 성공/거부 사유, 확보 수량, 모집 완료 여부
├── NoLockQuantityGate.java              # 벤치마크 전용, benchmark 프로파일에서만
├── PessimisticQuantityGate.java
├── OptimisticQuantityGate.java
├── ConditionalUpdateQuantityGate.java
└── QuantityGateConfig.java              # camgong.quantity-gate 로 구현체 선택
src/test/java/com/camgong/groupbuy/
├── JoinParticipationIT.java
├── ChangeParticipationIT.java
├── LeaveParticipationIT.java
├── IdempotentJoinIT.java
├── QuantityRaceIT.java                  # 안전한 3방식 × 동시 요청, 불변식 검증
└── ParticipationSerializationIT.java    # 001 수정·취소와의 직렬화
load/
├── k6/last-seat.js                      # 남은 수량 1, 100명 동시
├── k6/mixed.js                          # 참여·변경·취소·수정 섞기
└── seed.sql
docs/benchmarks/
└── participation-concurrency.md         # 측정 결과(구현 후)
```

**Structure Decision**: 게이트는 `group_buy` 행(상태·마감·가격·수량)만 책임지고, 참여 행 변경과 이벤트는 서비스가 같은 트랜잭션에서 한다. 이렇게 나누면 4방식 차이가 게이트 한 곳에 갇힌다.

## Complexity Tracking

| 항목 | 이유 | 더 단순한 안을 안 쓴 이유 |
|------|------|--------------------------|
| 인터페이스 `QuantityGate` + 구현체 4개 | 헌법 II가 4방식 구현·비교를 요구한다 | 구현체 하나로는 비교 자체가 불가능하다 |
