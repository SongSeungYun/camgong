# Implementation Plan: 공구 목록·상세 조회

**Branch**: `003-browse-groupbuy` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-browse-groupbuy/spec.md`

## Summary

공구 목록(비로그인 가능), 공구 상세(로그인 필수), 내 활동(내가 연 공구 / 내가 참여한 공구)을 읽기 전용 API로 제공한다.
목록은 상태·카테고리 필터, 최신순/마감 임박순 정렬, 제목·상품명 검색, 키셋 방식의 '더 보기'를 지원한다.
이 기능은 헌법 II의 **N+1 측정 대상**이다. 순진한 구현의 쿼리 수를 먼저 재고, 단계별로 개선하며 전후를 기록한다.

핵심 설계 결정(사용자 결정, 근거는 [research.md](research.md)):

- N+1 전후 비교는 **커밋 이력으로 남기고**, 코드에는 최종 구현과 **쿼리 수 회귀 테스트**를 둔다.
- 엔티티는 **읽기 전용 연관관계를 Long id 필드와 병행**한다(쓰기는 그대로, 읽기에서만 지연 로딩 연관관계 사용).
- 검색은 **`LIKE` 부분 일치**다(단어별 AND, `%`·`_` 이스케이프).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: 001 plan과 같다. 새 의존성 없음(동적 조건은 JPA Criteria API, 쿼리 수 측정은 Hibernate `StatementInspector`)

**Storage**: MySQL 8.4. 새 테이블 없음. 인덱스 2개 추가 (data-model.md)

**Testing**: JUnit 5, Testcontainers(MySQL). 쿼리 수는 테스트용 `StatementInspector`로 센다

**Target Platform**: 로컬 Docker

**Project Type**: web-service (읽기 API)

**Performance Goals**: SC-001 공구 1만 건에서 목록 첫 화면 95%가 1초 이내. SC-007 100건과 1만 건의 응답 시간 차이 2배 이내

**Constraints**: FR-007 이어서 볼 때 중복·누락 없음, 전체 건수 미제공. FR-011·012 링크·명단 공개 규칙. 상세·내 활동의 쿼리 수가 데이터 양(참여자 수, 페이지 항목 수)에 비례해 늘지 않을 것

**Scale/Scope**: 공구 1만 건, 공구당 참여자 수십 명, 한 페이지 20건

## Constitution Check

*GATE: Phase 0 전 통과 필요. Phase 1 설계 후 재확인.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. 범위 상한 | 통과 | 목록·상세·검색·카테고리 필터는 MVP의 "목록·상세" 범위다. 이미지, 전체 건수, 실시간 갱신은 넣지 않았다 |
| II. 측정으로 증명 | 통과 | N+1 시나리오 2개와 개선 단계, 쿼리 수 측정 도구, 기록 문서를 research.md 결정 10~11에서 확정했다 |
| III. 설명 가능한 설계 | 통과 | 결정마다 대안·이유·설명 체크가 있다 |
| IV. 핵심 로직 테스트 | 통과 (해당 없음) | 이 기능은 필수 대상(수량 초과, 전환, 동시 경쟁, 스케줄러)이 없다. 가시성 규칙과 쿼리 수는 통합 테스트로 덮는다 |

**설계 후 재확인 (Phase 1)**: 위 판정 그대로 통과. 새 의존성 없음.

## Project Structure

### Documentation (this feature)

```text
specs/003-browse-groupbuy/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── browse-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### Source Code (repository root)

```text
src/main/java/com/camgong/groupbuy/
├── GroupBuyQueryController.java      # GET 목록, 상세, 내 활동 (001의 최소 상세 GET을 대체)
├── GroupBuyQueryService.java         # 조회 흐름, 표시 상태·공개 규칙 적용
├── GroupBuyListQuery.java            # 요청 파라미터 record (status, category, q, sort, cursor, size)
├── GroupBuySearchRepository.java     # Criteria API 동적 조건 + 키셋 조회 (카운트 쿼리 없음)
├── PageCursor.java                   # 불투명 커서 인코딩/디코딩
├── DisplayStatus.java                # OPEN, PENDING_CLOSE, COMPLETED, FAILED 계산
├── GroupBuySummary.java              # 목록 카드
├── GroupBuyDetail.java               # 상세 응답
├── ParticipantView.java              # 개설자용 명단 행
├── MyActivityItem.java               # 내 활동 항목(내 수량 포함)
├── GroupBuy.java                     # (수정) organizer 읽기 전용 연관관계
├── Participation.java                # (수정) groupBuy, member 읽기 전용 연관관계
└── ParticipationRepository.java      # (수정) 내 활동·명단·건수 쿼리
src/main/resources/
└── schema.sql                        # (수정) 인덱스 2개
src/test/java/com/camgong/
├── support/QueryCountInspector.java  # StatementInspector 기반 쿼리 수 카운터
└── groupbuy/
    ├── ListGroupBuyIT.java           # US1: 노출 대상, 필터, 정렬, 검색, 페이지 넘김
    ├── GroupBuyDetailIT.java         # US2: 필드, 내 금액, 링크·명단 공개 규칙, 401·404
    ├── MyActivityIT.java             # US3
    ├── PaginationConsistencyIT.java  # 넘기는 중 변경해도 중복·누락 없음
    └── QueryCountIT.java             # N+1 시나리오별 쿼리 수 상한 회귀 테스트
docs/benchmarks/
└── browse-n-plus-one.md              # 단계별 쿼리 수와 커밋 해시 (구현 후)
```

**Structure Decision**: 읽기 전용 기능이라 조회 컨트롤러·서비스를 따로 둔다(쓰기 쪽 `GroupBuyService`와 분리). 동적 목록 조건은 별도 리포지토리 클래스(`GroupBuySearchRepository`)에 모은다.

## Complexity Tracking

헌법 위반 없음.
