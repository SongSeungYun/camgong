---

description: "공구 개설 구현 태스크"
---

# Tasks: 공구 개설

**Input**: `/specs/001-create-groupbuy/` 의 plan.md, spec.md, research.md, data-model.md, contracts/groupbuy-api.md, quickstart.md

**Prerequisites**: plan.md, spec.md

**Tests**: 헌법 IV가 핵심 로직(수량 초과 판정, 모집 완료 전환, 중복·동시 요청)의 Testcontainers 통합 테스트를 필수로 정하므로 테스트 태스크를 포함한다. 각 스토리에서 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.

**Organization**: 사용자 스토리별로 묶었다. 이 프로젝트는 `implement`를 쓰지 않고 사용자가 직접 구현한다.
**(핵심 로직)** 표시가 붙은 태스크는 락·쿼리·상태 전이를 다루므로 직접 쓴다. 나머지는 보일러플레이트라 AI에게 맡겨도 된다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 태스크에 의존하지 않아 병렬 가능
- **[Story]**: US1(개설), US2(잘못된 입력 거부), US3(수정·취소)
- 경로의 `main/`은 `src/main/java/com/camgong/`, `test/`는 `src/test/java/com/camgong/`, `res/`는 `src/main/resources/` 를 뜻한다.

## Phase 1: Setup

**Purpose**: 프로젝트 뼈대와 테스트 기반

- [ ] T001 `specs/001-create-groupbuy/research.md`의 "설명 체크" 4문항(취소 처리, 확보 수량 컬럼, 중복 가드, 수정 락)에 직접 답해 보기. 헌법 III에 따라 구현 전에 끝낸다.
- [ ] T002 start.spring.io로 Gradle 프로젝트 생성: Java 21, Spring Boot 최신 안정판, group `com.camgong`, 의존성 Spring Web / Spring Data JPA / Validation / MySQL Driver / Testcontainers. 산출물 `build.gradle`, `main/CamgongApplication.java`
- [ ] T003 [P] `res/application.yml` 작성: datasource는 환경변수 `DB_URL`, `DB_USER`, `DB_PASSWORD`(로컬 기본값 포함. `DB_URL` 기본값은 `?useAffectedRows=true`로 끝나야 하며 중복 가드 판정에 필요하니 지우지 말 것), `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`, `spring.sql.init.mode=always`, `spring.mvc.problemdetails.enabled=true`
- [ ] T004 [P] `test/support/IntegrationTestBase.java` 작성: `mysql:8.4` Testcontainers(`@ServiceConnection` 대신 `@DynamicPropertySource`로 `spring.datasource.url = container.getJdbcUrl() + "?useAffectedRows=true"` 지정), 시간을 직접 움직일 수 있는 `MutableClock`(java.time.Clock 상속)을 `@Primary` 빈으로 등록, 각 테스트 전에 `participation`, `group_buy`, `create_guard`, `member`를 비우고 회원 1(개설자)·2(참여자)·3(외부인)을 시드

---

## Phase 2: Foundational (모든 스토리의 선행 조건)

**Purpose**: 스키마, 엔티티 뼈대, 공통 오류 처리

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 스토리 작업을 시작하지 않는다.

- [ ] T005 `res/schema.sql` 작성 (모든 테이블 `CREATE TABLE IF NOT EXISTS`, 앱 재시작에 안전해야 함). data-model.md 제약을 그대로 옮긴다:
  - `member(id BIGINT PK, name)`
  - `group_buy(id BIGINT AUTO_INCREMENT PRIMARY KEY, organizer_id BIGINT FK→member, title VARCHAR(100) NOT NULL, description VARCHAR(2000) NULL, item_name VARCHAR(100) NOT NULL, unit_price INT NOT NULL CHECK (unit_price BETWEEN 1 AND 1000000), total_quantity INT NOT NULL, secured_quantity INT NOT NULL, deadline DATETIME(6) NOT NULL, item_url VARCHAR(500) NOT NULL, open_chat_url VARCHAR(500) NOT NULL, status VARCHAR(20) NOT NULL, version BIGINT NOT NULL, created_at DATETIME(6), updated_at DATETIME(6), CHECK (secured_quantity BETWEEN 1 AND total_quantity))`
  - `participation(id BIGINT AUTO_INCREMENT PRIMARY KEY, group_buy_id FK→group_buy, member_id FK→member, quantity INT CHECK (quantity > 0), created_at DATETIME(6), UNIQUE (group_buy_id, member_id))`
  - `create_guard(member_id BIGINT, fingerprint CHAR(64), created_at DATETIME(6), PRIMARY KEY (member_id, fingerprint))`
  - 파일 상단에 `-- ponytail: Flyway로 올릴 시점은 운영 데이터가 생기는 5단계 전` 주석
- [ ] T006 [P] `main/member/Member.java`, `main/member/MemberRepository.java`: 필드 `id`, `name`만
- [ ] T007 [P] `main/participation/Participation.java`, `main/participation/ParticipationRepository.java`: 필드 `id`, `groupBuyId`, `memberId`, `quantity`, `createdAt`. 연관관계 매핑 없이 `Long` 컬럼만 둔다(N+1 비교용 매핑은 조회 기능에서 결정). `id`는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`, `createdAt`은 서비스가 `Instant.now(clock)`으로 설정
- [ ] T008 `main/groupbuy/GroupBuyStatus.java`(`OPEN`, `COMPLETED`, `CANCELLED`), `main/groupbuy/GroupBuy.java`: data-model.md의 필드 전부, `organizerId`는 `Long` 컬럼, `@Version version`, `status`는 `EnumType.STRING`, `id`는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`. `createdAt`·`updatedAt`은 DB 기본값이나 `@CreatedDate`를 쓰지 않고 서비스가 `Instant.now(clock)`으로 설정한다('개설 시점 +30일' 판정은 이 `createdAt` 기준). 상태 전이 메서드는 US3에서 추가
- [ ] T009 [P] `main/CamgongApplication.java`에 `Clock` 빈(`Clock.systemUTC()`) 등록
- [ ] T010 [P] `main/common/ApiException.java`(상태 코드, title, `errors` 목록을 가진 런타임 예외)와 `main/common/ApiExceptionHandler.java`: `ProblemDetail`에 `errors: [{field, message}]` 속성을 담아 응답. `MethodArgumentNotValidException`→400(필드별 메시지), `ApiException`→지정한 상태. contracts의 코드 `DUPLICATE_REQUEST`, `NOT_MODIFIABLE`은 409의 `title`

**Checkpoint**: 앱이 뜨고 `schema.sql`이 `ddl-auto=validate`를 통과한다.

---

## Phase 3: User Story 1 - 공구 개설하기 (Priority: P1) 🎯 MVP

**Goal**: 로그인 회원이 공구를 열고, 개설자가 자동 참여되며, 10초 안 중복 요청은 하나만 만들어진다.

**Independent Test**: 회원 1이 총 수량 24, 개설자 수량 9로 개설하고 GET으로 확보 9, 남은 15, `OPEN`을 확인한다. 같은 본문을 동시에 보내면 공구가 1건이다.

### Tests for User Story 1 ⚠️ (먼저 작성하고 실패 확인)

- [ ] T011 [P] [US1] `test/groupbuy/CreateGroupBuyIT.java`: quickstart 시나리오 1(201, `Location`, `securedQuantity=9`, `remainingQuantity=15`, `status=OPEN`, 요청한 `itemUrl`·`openChatUrl` 그대로, `participation`에 개설자 1행·수량 9), 시나리오 2(헤더 없음, 존재하지 않는 회원 id → 401)
- [ ] T012 [P] [US1] `test/groupbuy/CreateGuardIT.java`: 시나리오 7(같은 본문을 동시에 N개 → 201 정확히 1건, 나머지 409 `DUPLICATE_REQUEST`, `group_buy` 1행), 시나리오 8(`MutableClock`을 11초 뒤로 → 같은 본문이 201, 공구 2건), 입력값 하나만 달라도 별개 공구, `CreateGuard`를 같은 트랜잭션에서 같은 입력으로 연달아 호출하면 두 번째가 `false`(영향 행 0)이고 11초 뒤에는 `true`

### Implementation for User Story 1

- [ ] T013 [P] [US1] `main/groupbuy/GroupBuyRequest.java`: record로 필드만 정의 — `title`, `description`, `itemName`, `unitPrice`, `totalQuantity`, `deadline`(`OffsetDateTime`), `organizerQuantity`, `itemUrl`, `openChatUrl`. 제약은 US2에서 추가
- [ ] T014 [P] [US1] `main/groupbuy/GroupBuyResponse.java`: contracts의 응답 필드. `remainingQuantity = totalQuantity − securedQuantity`, `organizerQuantity`, `deadline`은 오프셋 포함 ISO-8601, `openChatUrl`은 호출자가 가시성 판단 후 값 또는 null을 넘긴다
- [ ] T015 [US1] `main/groupbuy/GroupBuyRepository.java`: `JpaRepository<GroupBuy, Long>` (T008 이후)
- [ ] T016 [US1] `main/groupbuy/CreateGuard.java` **(핵심 로직)**: `JdbcClient`로 research.md 결정 3의 `INSERT ... ON DUPLICATE KEY UPDATE ... IF(create_guard.created_at < :now - INTERVAL 10 SECOND, new.created_at, create_guard.created_at)`(행 별칭 `AS new`, 기존 값은 테이블 이름을 붙여 참조) 실행, 영향 행 수 0이면 중복. 결과는 `boolean`으로 노출하고, 이 판정은 JDBC `useAffectedRows=true`를 전제한다. `now`는 DB `NOW()`가 아니라 `Clock`에서 파라미터로 전달. 입력값(제목, 설명(null→""), 상품명, 가격, 총 수량, 마감 Instant, 개설자 수량, 구매처 링크, 오픈채팅 링크)을 trim해 이어 붙인 문자열의 SHA-256 hex(`MessageDigest`)를 fingerprint로 만드는 메서드 포함. 주석: `-- ponytail: MySQL 전용 문법, 가드 행 정리 작업 없음`
- [ ] T017 [US1] `main/groupbuy/GroupBuyService.java`의 `create` **(핵심 로직)**: `@Transactional`. 회원 존재 확인(없으면 401) → 가드 호출(중복이면 409 `DUPLICATE_REQUEST`) → `GroupBuy`(`OPEN`, `securedQuantity=organizerQuantity`) 저장 → 개설자 `Participation`(수량=개설자 수량) 저장 → 응답. `createdAt`·`updatedAt`과 시간 판정 모두 주입한 `Clock`으로 설정(DB 기본값 사용 안 함)
- [ ] T018 [US1] `main/groupbuy/GroupBuyService.java`의 `get`: 존재하지 않거나 `CANCELLED`이면 404. `openChatUrl`은 data-model/contracts 규칙대로 — 개설자는 `OPEN`·`COMPLETED`에서 값, 참여자는 `COMPLETED`에서만 값, 그 외 null. 개설자 수량은 개설자의 `Participation`에서 조회
- [ ] T019 [US1] `main/groupbuy/GroupBuyController.java`: `POST /api/groupbuys`(201 + `Location`), `GET /api/groupbuys/{id}`. 요청자는 `@RequestHeader(value = "X-Member-Id", required = false) Long memberId`. 주석: `// ponytail: 임시 인증, 4단계에서 로그인으로 교체`

**Checkpoint**: US1이 단독으로 동작한다. T011·T012가 통과한다.

---

## Phase 4: User Story 2 - 잘못된 입력 거부 (Priority: P2)

**Goal**: 규칙을 어긴 입력은 공구를 만들지 않고 필드별 사유를 돌려준다.

**Independent Test**: 규칙 위반 입력을 하나씩 보내 400과 `errors[].field`를 확인하고, `group_buy`와 `create_guard`에 행이 남지 않음을 확인한다.

### Tests for User Story 2 ⚠️

- [ ] T020 [P] [US2] `test/groupbuy/CreateGroupBuyValidationIT.java`: 시나리오 3(과거 마감 → `deadline`), 4(총 24·개설자 24 → `organizerQuantity`), 5(제목 공백, 총 수량 0 → 필드별 사유), 6(`openChatUrl=ftp://x` 또는 비움 → `openChatUrl`, `itemUrl` 누락·형식 오류 → `itemUrl`). 경계: 총 수량 1·2·1000·1001, 가격 0·1·1,000,000·1,000,001, 마감 now+30일 정각과 +1초, 제목·상품명 100자와 101자. 실패한 요청이 `create_guard`에 행을 남기지 않는지, 같은 본문 재전송이 409가 아닌 400으로 동일하게 거부되는지

### Implementation for User Story 2

- [ ] T021 [US2] `main/groupbuy/GroupBuyRequest.java`에 제약 추가 + `GroupBuyController.java`의 POST에 `@Valid`:
  - `title`: 필수, 공백만 불가, 최대 100자 (`@NotBlank @Size(max = 100)`)
  - `description`: 선택, 최대 2000자
  - `itemName`: 필수, 공백만 불가, 최대 100자
  - `unitPrice`: 필수, 1 이상 1,000,000 이하 정수 (`@NotNull @Min(1) @Max(1_000_000)`)
  - `totalQuantity`: 필수, 2 이상 1000 이하 (`@NotNull @Min(2) @Max(1000)`)
  - `deadline`: 필수 (`@NotNull`)
  - `organizerQuantity`: 필수, 1 이상 (`@NotNull @Min(1)`)
  - `itemUrl`: 필수, 최대 500자, `https://`로 시작 (`@NotBlank @Size(max = 500) @Pattern`)
  - `openChatUrl`: 필수, 최대 500자, `https://`로 시작 (`@NotBlank @Size(max = 500) @Pattern`)
- [ ] T022 [US2] `main/groupbuy/GroupBuyService.java`에 `validateForCreate` **(핵심 로직)**: `deadline > now` 그리고 `deadline ≤ now + 30일`, `organizerQuantity < totalQuantity`. 위반은 모두 모아 `errors[]`로 400. **`create`에서 가드 호출보다 먼저 실행**한다(실패한 요청이 가드를 소모하면 안 됨)

**Checkpoint**: US1·US2가 함께 통과한다. **여기까지가 권장 MVP.**

---

## Phase 5: User Story 3 - 개설한 공구 수정·취소 (Priority: P3)

**Goal**: 개설자가 마감 전 공구를 고치거나 취소한다. 수량이 딱 차면 모집이 끝나고, 동시 요청에도 일관된다.

**Independent Test**: 개설자가 수정 후 값이 반영되는지, 수량을 딱 채우면 `COMPLETED`가 되고 이후 수정·취소가 409인지, 취소 후 GET이 404인지 확인한다.

### Tests for User Story 3 ⚠️

참여자 행은 SQL로 직접 시드한다(`participation` 행과 `group_buy.secured_quantity`를 함께 맞춘다).

- [ ] T023 [P] [US3] `test/groupbuy/UpdateGroupBuyIT.java`: 시나리오 9(가격·개설자 수량 변경, `securedQuantity` 갱신), 10(비개설자 403), 11(마감 단축 400 `deadline`), 12(개설 +30일 초과 연장 400), 13(총 수량을 확보 수량 아래로 400), 14(개설자 수량을 올려 합이 총 수량과 같아지면 `COMPLETED`), 15(완료 후 PUT → 409 `NOT_MODIFIABLE`), 수량 0 거부, 개설자 수량 증가로 합이 총 수량 초과 시 400
- [ ] T024 [P] [US3] `test/groupbuy/CancelGroupBuyIT.java`: 시나리오 16(204, 이후 GET 404), 17(`MutableClock`을 마감 이후로 → PUT·cancel 409), 비개설자 403, 이미 취소·완료된 공구 409
- [ ] T025 [P] [US3] `test/groupbuy/LinkVisibilityIT.java`: 시나리오 19 — 개설자·참여자·외부인 × `OPEN`·`COMPLETED` 조합의 `openChatUrl` 노출 표(contracts)를 그대로 검증
- [ ] T026 [P] [US3] `test/groupbuy/NotificationEventIT.java`: 시나리오 20 — 참여자 2명 있는 공구 수정·취소 시 커밋 후 이벤트 1건, 대상이 개설자 제외 참여자. 오픈채팅 링크만 수정하거나 개설자만 참여한 경우 이벤트 없음. 검증 실패로 롤백된 수정은 이벤트 없음
- [ ] T027 [P] [US3] `test/groupbuy/ModifyConcurrencyIT.java`: 시나리오 18 — 수정과 취소 동시 실행 시 한쪽만 성공하고 취소됨이면 수정은 409. 수정 N개 동시 실행(개설자 수량 증가 경쟁)에서도 `secured_quantity ≤ total_quantity` 유지, `COMPLETED` 전환은 정확히 한 번. 참여×수정·취소 동시성은 참여 기능에서 검증한다

### Implementation for User Story 3

- [ ] T028 [P] [US3] `main/groupbuy/GroupBuyEvents.java`: `GroupBuyChanged(groupBuyId, recipientIds, changedFields)`, `GroupBuyCancelled(groupBuyId, recipientIds)` record
- [ ] T029 [US3] `main/groupbuy/GroupBuyRepository.java`에 `findByIdForUpdate`(`@Lock(PESSIMISTIC_WRITE)`), `main/participation/ParticipationRepository.java`에 공구별 참여 목록 조회, (공구, 회원)으로 개설자 참여 행 조회
- [ ] T030 [US3] `main/groupbuy/GroupBuy.java`에 상태 전이 메서드 **(핵심 로직)**: `completeIfFull()`(`securedQuantity == totalQuantity`이면 `COMPLETED`, 이미 `COMPLETED`면 변화 없음), `cancel()`(`OPEN`일 때만 `CANCELLED`). 허용 조건은 `status = OPEN` 이고 `deadline > now`
- [ ] T031 [US3] `main/groupbuy/GroupBuyService.java`의 `update` **(핵심 로직)**: `findByIdForUpdate`로 락 → data-model.md "수정 시 검증 순서" 그대로(존재 404 → 개설자 403 → `OPEN`·마감 전 아니면 409 `NOT_MODIFIABLE` → 마감 `new ≥ old` & `≤ created_at + 30일` → 수량 `organizerQuantity ≥ 1`, `secured' = secured − old개설자 수량 + new개설자 수량`, `secured' ≤ total'` → 반영 후 `completeIfFull`). 개설자 `Participation`과 `secured_quantity`를 같은 트랜잭션에서 함께 갱신하고 `updatedAt`은 `Instant.now(clock)`으로 설정(30일 상한은 저장된 `createdAt` 기준). 오픈채팅 링크 외 필드(구매처 링크 포함)가 바뀌고 개설자 외 참여자가 있으면 `GroupBuyChanged` 발행
- [ ] T032 [US3] `main/groupbuy/GroupBuyService.java`의 `cancel` **(핵심 로직)**: 같은 락 → 존재 404 → 개설자 403 → `OPEN`·마감 전 아니면 409 → `cancel()` → 개설자 외 참여자가 있으면 `GroupBuyCancelled` 발행. 행은 지우지 않는다
- [ ] T033 [P] [US3] `main/groupbuy/GroupBuyEventLogger.java`: `@TransactionalEventListener(AFTER_COMMIT)`으로 두 이벤트를 로그만 남긴다. 주석: `// ponytail: 커밋 후 유실 가능, 알림 기능에서 전달 수단 연결·신뢰성 필요하면 아웃박스로`
- [ ] T034 [US3] `main/groupbuy/GroupBuyController.java`에 `PUT /api/groupbuys/{id}`(200, `@Valid` 본문, 응답은 contracts 응답 본문), `POST /api/groupbuys/{id}/cancel`(204)

**Checkpoint**: 세 스토리가 모두 통과한다.

---

## Phase 6: Polish & Cross-Cutting

- [ ] T035 `./gradlew test` 전체 통과 확인 후 quickstart.md 시나리오 1~20을 수동(`bootRun` + curl 또는 HTTP 파일)으로도 한 번 실행해 기대 결과와 대조
- [ ] T036 [P] 헌법 IV 필수 대상 3종 — 수량 초과 판정(시나리오 4, 13), 모집 완료 전환(14, 15), 중복·동시 요청(7, 8, 18) — 이 통합 테스트로 존재하고 통과하는지 확인
- [ ] T037 [P] `ponytail:` 주석이 남은 한계(MySQL 전용 가드·가드 행 정리 없음, 임시 인증, 이벤트 유실 가능, `schema.sql`→Flyway)를 모두 담았는지 확인하고, plan.md Constraints에 '로그인 전 공개 배포·실제 공구 운영 금지'가 있는지 확인
- [ ] T038 [P] `specs/001-create-groupbuy/plan.md`의 소스 트리를 실제 파일 구성과 대조해 갱신

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001은 언제든 선행 가능. T003·T004는 T002 이후
- **Foundational (Phase 2)**: Setup 완료 후. 모든 스토리를 막는다
- **US1 → US2 → US3** 순서로 진행한다. US2는 US1의 `GroupBuyRequest`·`create`를 고치고, US3는 US1의 개설 결과를 전제로 하며 셋 모두 `GroupBuyService.java`·`GroupBuyController.java`를 공유하므로 병렬로 진행하지 않는다
- **Polish**: 원하는 스토리가 끝난 뒤

### Within Each Story

- 테스트 먼저 작성하고 실패 확인
- 엔티티·레포지토리 → 서비스 → 컨트롤러

### 태스크 간 의존

- T008은 T005(스키마)와 대조되어야 `validate`가 통과한다
- T016(가드)와 T017(create)은 T015 이후. T022(검증)는 T017 이후이며 가드보다 먼저 호출되어야 한다
- T030은 T008 이후. T031·T032는 T029, T030, T028 이후

## Parallel Example

```bash
# Foundational 중 서로 다른 파일
T006 Member / T007 Participation / T009 Clock 빈 / T010 예외 처리

# US1 테스트 두 개
T011 CreateGroupBuyIT / T012 CreateGuardIT

# US3 테스트 다섯 개와 이벤트 record
T023 UpdateGroupBuyIT / T024 CancelGroupBuyIT / T025 LinkVisibilityIT
T026 NotificationEventIT / T027 ModifyConcurrencyIT / T028 GroupBuyEvents
```

## Implementation Strategy

### MVP First

1. Setup + Foundational
2. US1 → US2 (여기서 멈추고 검증: 개설과 입력 거부 완성)
3. US3는 참여/취소 기능과 맞물리는 상태 전이(`completeIfFull`)를 포함하므로, 참여 기능 spec 작업 전에 하는 것을 권장

### 시간 상한 (헌법 I)

막히면 화면·배포가 먼저 줄고 핵심 API와 측정은 줄이지 않는다. 이 기능에서 줄일 수 있는 것은 T035의 수동 실행, T038뿐이다.

## Notes

- **(핵심 로직)** 태스크는 락·쿼리·상태 전이를 직접 쓴다. 나머지는 AI 보조 가능
- 각 태스크 또는 논리적 묶음 후 커밋한다(저장소가 아직 git 저장소가 아니므로 먼저 `git init`)
- 체크포인트마다 멈춰서 스토리 단독 동작을 검증한다
