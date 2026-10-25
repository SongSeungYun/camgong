# Quickstart: 공구 목록·상세 조회 검증

구현 후 이 기능이 end-to-end로 동작하는지 확인하는 시나리오다. 요청·응답은 [contracts/browse-api.md](contracts/browse-api.md),
규칙은 [data-model.md](data-model.md)를 본다. 001(개설), 004(참여), 005(마감 확정)가 있으면 상태별 공구를 API로 만들 수 있고, 없으면 SQL로 시드한다.
시간은 테스트의 `MutableClock`으로 움직인다.

## 준비

- 001 quickstart의 준비(JDK 21, Docker, `useAffectedRows=true`).
- 회원: 개설자 1, 참여자 2~4, 외부인 5.
- 공구 시드: 상태별(`OPEN`, 마감 지난 `OPEN`, `COMPLETED`, `FAILED`, `CANCELLED`) 각 여러 개, 카테고리 분산.
- 자동 테스트: `./gradlew test`.

## 시나리오

| # | 확인 내용 | 요청 | 기대 결과 | spec |
|---|-----------|------|-----------|------|
| 1 | 비로그인 목록 | 헤더 없이 `GET /api/groupbuys` | 200, 모집 중 공구 카드(제목, 상품명, 가격, 확보/총, 마감, 상태, 카테고리) | US1-1, FR-001 |
| 2 | 취소 제외 | 취소된 공구 존재 | 어느 목록·필터에도 없음 | US1-2, FR-002 |
| 3 | 마감됨 표시 | 마감 지난 `OPEN` 공구, `state=PENDING_CLOSE` | `state=PENDING_CLOSE`로 표시, 기본(`OPEN`) 목록에는 없음 | US1-3, FR-004 |
| 4 | 마감 임박순 | `sort=deadline` | 마감이 이른 공구가 먼저, 같은 마감은 id 순. 어떤 `state` 필터에서도 같은 규칙 | US1-4, FR-005 |
| 5 | 끝까지 넘기기 | 공구 45건, `size=20`으로 `nextCursor`를 따라가기 | 3페이지에 걸쳐 45건이 한 번씩, 마지막 `nextCursor=null` | US1-5, FR-007, SC-003 |
| 6 | 카테고리 필터 | `category=FOOD` | 식품 공구만 | US1-6, FR-017 |
| 7 | 검색 | `q=햇`, `q=햇반`, `q=24 햇반`, `q=24 라면` | '햇'·'햇반'은 해당 공구, '24 햇반'은 제목 '햇반 24개 공구'를 찾음, '24 라면'은 없음 | US1-7·8, FR-018 |
| 8 | 검색 특수문자 | `q=%`, `q=_` | 와일드카드로 동작하지 않고 문자 그대로 찾음(없으면 빈 결과) | 엣지 케이스 |
| 9 | 잘못된 값 | `sort=x`, `size=100`, `q` 51자, 잘못된 커서 | 400 | 엣지 케이스 |
| 10 | 필터 조합 | `state=COMPLETED&category=FOOD&q=햇반&sort=deadline` | 조건을 모두 만족하는 공구, 넘기기도 정상 | FR-006·017·018 |
| 11 | 이어서 보는 중 변경 | 1페이지를 본 뒤 새 공구 개설·상태 변화, 이어서 2페이지 | 이미 본 공구가 다시 나오지 않음 | FR-007 |
| 12 | 상세(일반 회원) | 회원 5가 `GET /api/groupbuys/{id}` | 200, 개설자 이름·구매처 링크·수량·마감·상태·`participantCount`, `participants=null`, `myParticipation=null`, `openChatUrl=null` | US2-1·3, FR-009 |
| 13 | 내 부담 금액 | 총 24, 내 수량 6, 개당 1,200 | `myParticipation={quantity:6, amount:7200}` | US2-2, FR-010 |
| 14 | 개설자 명단 | 개설자가 상세 | `participants`에 참여자별 이름·수량(개설자 포함), `isOrganizer=true` | US2-4, FR-012 |
| 15 | 링크 공개 | `OPEN` 공구, `COMPLETED` 공구 × 개설자·참여자·외부인 | 개설자는 `OPEN`·`COMPLETED`에서 링크, 참여자는 `COMPLETED`에서만, 외부인·`FAILED`·`PENDING_CLOSE`의 참여자는 `null` | US2-5, FR-011 |
| 16 | 비로그인 상세 | 헤더 없음 | 401 | US2-6, FR-008 |
| 17 | 없는 공구 | 없는 id, 취소된 id | 404 | US2-7, FR-013 |
| 18 | 내 활동 | 회원이 2개 개설하고 3개 참여 | `role=organizer` 2건, `role=participant` 5건(개설한 2건 포함), 개설 시각 늦은 순, 상태 뱃지(`state`) | US3-1, FR-014 |
| 19 | 내 활동의 취소 | 내가 참여한 공구를 개설자가 취소 | 두 목록 모두에서 사라짐 | US3-2 |
| 20 | 내 활동 비로그인 | 헤더 없음 | 401 | US3-3 |
| 21 | 수치 일관 | 같은 공구의 목록·상세·내 활동 | 확보/남은 수량, 상태 일치 | FR-015, SC-006 |

## N+1 측정 (헌법 II)

`QueryCountIT`가 `QueryCountInspector`로 요청당 쿼리 수를 센다. 개선 단계마다 커밋하고 표를 채운다.

| 시나리오 | N | ① 순진한 구현 | ② fetch join | ③ batch size | ④ DTO 프로젝션 | 최종 상한 |
|----------|---|---------------|--------------|--------------|----------------|-----------|
| A 내가 참여한 공구 한 페이지 | 1, 20, 50 | (측정) | (측정) | (측정) | (측정) | 2 이하 |
| B 상세의 개설자용 명단 | 1, 20, 50 | (측정) | (측정) | (측정) | (측정) | 4 이하 |

결과(쿼리 수, 커밋 해시, 해석)는 `docs/benchmarks/browse-n-plus-one.md`에 남긴다.

## 응답 시간 측정 (SC-001, SC-007)

```bash
# 공구 100건 / 1만 건 시드(load/seed-browse.sql) 후 k6로 목록 첫 페이지와 검색 측정
k6 run load/k6/browse.js -e DATASET=100
k6 run load/k6/browse.js -e DATASET=10000
```

| 측정 | 기대 |
|------|------|
| 1만 건 목록 첫 화면 p95 (일반·검색·드문 검색어) | 1초 이내 (SC-001) |
| 100건 대비 1만 건 응답 시간 | 2배 이내 (SC-007) |

## 헌법 II와의 대응

| 요구 | 시나리오 |
|------|----------|
| N+1 개선 전후 쿼리 수 기록 | A, B 표 |
| 측정 방법·환경·결과를 문서로 | `docs/benchmarks/browse-n-plus-one.md` |
