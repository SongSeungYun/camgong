# Implementation Plan: 공구 개설

**Branch**: `001-create-groupbuy` | **Date**: 2026-10-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-create-groupbuy/spec.md`

## Summary

로그인 회원이 공구(상품 1종, 총 수량을 참여자가 수량 단위로 분할)를 개설하고, 마감 전까지 수정·취소한다.
확보 수량이 총 수량에 닿는 순간 모집 완료로 전환한다. Spring Boot REST API 한 모듈로 만든다.

핵심 설계 결정 4개(사용자 결정, 근거는 [research.md](research.md)):

- 취소는 상태 전환(`CANCELLED`)만 한다. 물리 삭제 없음.
- 확보 수량은 공구 행의 `secured_quantity` 컬럼으로 관리한다.
- 10초 중복 개설은 `create_guard` 테이블에 `INSERT ... ON DUPLICATE KEY UPDATE`로 원자적으로 막는다.
- 수정·취소는 공구 행 `SELECT ... FOR UPDATE`(비관적 락)로 직렬화한다.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.2 (Hibernate 7), spring-boot-starter-webmvc(Boot 4의 웹 스타터 이름), -data-jpa, -validation, MySQL Connector/J

**Storage**: MySQL 8.x. 스키마는 `src/main/resources/schema.sql`로 관리하고 `ddl-auto=validate`로 엔티티와 대조한다.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers(MySQL). 운영과 같은 MySQL 이미지를 고정해서 쓴다.

**Target Platform**: 단일 서버(로컬 Docker → 이후 Docker 배포)

**Project Type**: web-service (백엔드만. 화면은 4단계)

**Performance Goals**: 해당 없음. 이 기능은 쓰기 위주이고 측정 대상(N+1, 동시성 비교)은 조회·참여 기능에서 다룬다.

**Constraints**: FR-009 중복 가드는 동시 요청에도 원자적이어야 한다. FR-016 모집 완료 전환은 중복·초과가 없어야 한다. 가드 판정은 JDBC `useAffectedRows=true`를 전제한다. `X-Member-Id`는 위조할 수 있으므로 로그인(4단계) 전에는 공개 배포와 실제 공구 운영을 하지 않는다.

**Scale/Scope**: 기숙사 단위 수십~수백 명, 동시 접속 수십 명 이하

**임시 결정 (ponytail)**: 로그인이 4단계라 요청자는 `X-Member-Id` 헤더로 식별한다. 4단계에서 인증으로 교체한다.
`member` 테이블은 FK용 최소 형태(id, name)만 두고 가입 기능에서 확장한다.

## Constitution Check

*GATE: Phase 0 전 통과 필요. Phase 1 설계 후 재확인.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. 범위 상한 | 통과 | 앱 내 채팅·결제 없음. 구매처 링크와 오픈채팅 링크는 필수 필드 두 개와 공개 규칙뿐이다. 알림은 이벤트 발행까지만 하고 전달 수단은 알림 기능으로 미룬다. |
| II. 측정으로 증명 | 통과 (해당 없음) | 이 기능은 N+1·동시성 비교 대상이 아니다. 단, 락 선택과 `secured_quantity` 컬럼은 3단계 4방식 비교의 기반으로 설계했다. |
| III. 설명 가능한 설계 | 통과 | 결정 4개 모두 대안과 이유가 research.md에 있고 사용자가 직접 선택했다. 구현 전 사용자가 이유를 설명해 보는 단계가 남아 있다. |
| IV. 핵심 로직 테스트 | 통과 | 수량 초과 판정, 모집 완료 전환, 동시 수정·취소, 중복 가드는 Testcontainers(MySQL) 통합 테스트로 덮는다. |

**설계 후 재확인 (Phase 1)**: 위 판정 그대로 통과. 새 외부 의존성은 추가하지 않았다(Flyway 대신 `schema.sql`, 알림용 메시지 라이브러리 대신 Spring 이벤트).

## Project Structure

### Documentation (this feature)

```text
specs/001-create-groupbuy/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── groupbuy-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### Source Code (repository root)

```text
build.gradle
src/main/java/com/camgong/
├── CamgongApplication.java
├── common/
│   ├── ApiException.java
│   └── ApiExceptionHandler.java        # ProblemDetail 변환, 필드 오류 목록
├── member/
│   ├── Member.java
│   └── MemberRepository.java
└── groupbuy/
    ├── GroupBuy.java                   # 엔티티 + 상태 전이 메서드
    ├── GroupBuyStatus.java             # OPEN, COMPLETED, CANCELLED
    ├── GroupBuyCategory.java           # FOOD, DAILY, KITCHEN, HEALTH_BEAUTY, STATIONERY, ETC
    ├── Participation.java              # 참여 (공구의 일부)
    ├── ParticipationRepository.java    # 락 뒤 읽기용 findLocked* 포함
    ├── GroupBuyRepository.java         # 비관적 락 조회 포함 (findByIdForUpdate)
    ├── CreateGuard.java                # JdbcClient로 가드 INSERT
    ├── GroupBuyService.java            # 개설, 수정, 취소, 조회
    ├── GroupBuyController.java
    ├── GroupBuyRequest.java            # 개설·수정 공용 record
    ├── GroupBuyResponse.java
    ├── GroupBuyEvents.java             # Changed, Cancelled, Completed 이벤트 record
    └── GroupBuyEventLogger.java        # AFTER_COMMIT 로그 (알림 기능 전까지)
src/main/resources/
├── application.yml
└── schema.sql
src/test/java/com/camgong/
├── support/
│   ├── IntegrationTestBase.java        # MySQL Testcontainer, MutableClock 주입, 시드, as(memberId)
│   ├── MutableClock.java
│   ├── EventCollector.java             # AFTER_COMMIT 이벤트 수집
│   └── GroupBuyBodies.java             # 요청 본문 빌더
├── support/SchemaIT.java               # schema.sql ddl-auto=validate, 재실행 안전
└── groupbuy/
    ├── CreateGroupBuyIT.java
    ├── CreateGuardIT.java              # 중복 가드
    ├── CreateGroupBuyValidationIT.java
    ├── UpdateGroupBuyIT.java
    ├── CancelGroupBuyIT.java
    ├── LinkVisibilityIT.java
    ├── NotificationEventIT.java
    └── ModifyConcurrencyIT.java        # 수정 vs 수정, 취소 vs 수정
```

**Structure Decision**: 단일 Gradle 모듈, 기능별 패키지. 서비스·리포지토리 인터페이스를 따로 두지 않는다(구현체가 하나뿐이다).
`Participation`은 공구의 일부라 `groupbuy` 패키지에 둔다(확보 수량 불변식과 락이 공구 행 하나에 걸려 있다). `member`는 이 기능에 필요한 최소 엔티티만 두고 이후 기능에서 확장한다.

## Complexity Tracking

헌법 위반 없음.
