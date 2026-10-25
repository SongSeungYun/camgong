# Quickstart: 공구 개설 검증

구현 후 이 기능이 end-to-end로 동작하는지 확인하는 시나리오다. 요청·응답 형식은 [contracts/groupbuy-api.md](contracts/groupbuy-api.md),
테이블은 [data-model.md](data-model.md)를 본다. 프로젝트는 아직 생성 전이라 명령은 구현 후 기준이다.

## 준비

- JDK 21, Docker(Testcontainers와 로컬 MySQL용)
- 자동 테스트: `./gradlew test` — Testcontainers가 MySQL 컨테이너를 띄운다.
- 수동 확인용 실행:

  ```bash
  docker run -d --name camgong-mysql -p 3306:3306 \
    -e MYSQL_ROOT_PASSWORD=pw -e MYSQL_DATABASE=camgong mysql:8.4
  ./gradlew bootRun
  ```

  MySQL 이미지 태그는 테스트에서 쓰는 것과 같아야 한다(동작 차이 방지).
- 회원 시드: `INSERT INTO member (id, name) VALUES (1,'개설자'),(2,'참여자'),(3,'외부인');`

## 시나리오

각 요청에는 `X-Member-Id` 헤더를 붙인다(개설자는 1).

| # | 확인 내용 | 요청 | 기대 결과 | spec |
|---|-----------|------|-----------|------|
| 1 | 정상 개설 | POST: 총 수량 24, 개설자 수량 9, 마감 +7일 | 201, `securedQuantity=9`, `remainingQuantity=15`, `status=OPEN` | US1, SC-002 |
| 2 | 로그인 없음 | 헤더 없이 POST | 401 | FR-001 |
| 3 | 과거 마감 | 마감을 어제로 POST | 400, `errors`에 `deadline` | US2-1 |
| 4 | 개설자 수량 ≥ 총 수량 | 총 24, 개설자 24 | 400, `organizerQuantity` | US2-2, FR-007 |
| 5 | 필수 누락·수량 0 | 제목 비움, 총 수량 0 | 400, 필드별 사유 | US2-3, SC-003 |
| 6 | 링크 형식·누락 | `openChatUrl=ftp://x`, `itemUrl` 누락 | 400, `openChatUrl`, `itemUrl` | FR-017, FR-018 |
| 7 | 더블 클릭 | 같은 본문을 10초 안에 2번(동시 포함) | 하나만 201, 나머지 409 `DUPLICATE_REQUEST`. 공구 1건 | FR-009, SC-004 |
| 8 | 10초 뒤 재개설 | 7 이후 시계를 11초 뒤로 두고 같은 본문 | 201 (별개 공구) | FR-009 |
| 9 | 수정 | 개설자가 가격·개설자 수량 변경 | 200, 변경 반영, `securedQuantity` 갱신 | FR-012 |
| 10 | 타인 수정 | 회원 3이 PUT | 403 | FR-011 |
| 11 | 마감 단축 | 마감을 현재 값보다 앞당겨 PUT | 400, `deadline` | FR-012 |
| 12 | 마감 연장 상한 | 개설 +30일 초과로 연장 | 400, `deadline` | FR-004 |
| 13 | 수량 초과 수정 | 참여자 수량 합이 있는 상태에서 총 수량을 그 아래로 | 400 | FR-013 |
| 14 | 딱 채우는 수정 | 개설자 수량을 올려 합이 총 수량과 같게 | 200, `status=COMPLETED`, `GroupBuyCompleted` 1건(개설자 포함 전원) | FR-013, FR-016 |
| 15 | 완료 후 수정·취소 | 14 이후 PUT, cancel | 409 `NOT_MODIFIABLE` | FR-015 |
| 16 | 취소 | 개설자가 cancel | 204, 이후 GET 404 | FR-014 |
| 17 | 마감 지난 수정 | 시계를 마감 이후로 두고 PUT/cancel | 409 `NOT_MODIFIABLE` | FR-015 |
| 18 | 동시 수정·취소 | 같은 공구에 수정과 취소를 동시에 | 한쪽만 성공, 결과 상태가 일관됨(취소됨이면 수정은 404) | FR-014 |
| 19 | 링크 공개 | 공구를 COMPLETED로 만든 뒤 개설자·참여자·외부인이 GET | 개설자·참여자에게 링크, 외부인은 null. OPEN일 때 참여자는 null | FR-017, SC-007 |
| 20 | 알림 이벤트 | 참여자 2명이 있는 공구를 수정/취소(참여 행은 SQL로 삽입) | 커밋 후 이벤트 1건, 대상은 개설자 제외 참여자. 오픈채팅 링크만 수정하면 이벤트 없음 | FR-012, SC-006 |
| 21 | 카테고리 | 카테고리 누락, 목록 밖 값(`SNACK`) | 400, `category` | FR-003, FR-019 |

참여 기능이 아직 없으므로 시나리오 9·13·19·20의 참여자 행은 테스트 코드나 SQL로 직접 넣는다
(`participation`에 행을 넣고 `group_buy.secured_quantity`도 함께 맞춘다).

## 헌법 IV 필수 통합 테스트와의 대응

| 필수 대상 | 시나리오 |
|-----------|----------|
| 정원(수량) 초과 판정 | 4, 13 |
| 모집 완료 전환 | 14, 15 |
| 중복 방지·동시 요청 | 7, 8, 18 |
