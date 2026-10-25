# Quickstart: 공구 참여 검증

구현 후 이 기능이 end-to-end로 동작하는지 확인하는 시나리오다. 요청·응답은 [contracts/participation-api.md](contracts/participation-api.md),
규칙은 [data-model.md](data-model.md)를 본다. 001이 먼저 구현되어 있어야 한다(공구 개설).

## 준비

- 001 quickstart의 준비를 따른다(JDK 21, Docker, `useAffectedRows=true`).
- 회원 시드: 개설자 1, 참여자 2~101(동시성 시험용 100명).
- 공구 시드: 회원 1이 총 24, 개설자 수량 9, 개당 1,200원, 마감 +7일로 개설한다(`POST /api/groupbuys`).
- 자동 테스트: `./gradlew test`. 부하 측정: 아래 "측정" 절.

## 시나리오

각 요청에 `X-Member-Id`를 붙인다.

| # | 확인 내용 | 요청 | 기대 결과 | spec |
|---|-----------|------|-----------|------|
| 1 | 정상 참여 | 회원 2: 수량 6, `expectedUnitPrice=1200` | 201, `amount=7200`, `securedQuantity=15`, `remainingQuantity=9`, `status=OPEN` | US1-1, FR-003 |
| 2 | 딱 채우기 | 회원 3: 수량 9 | 201, `status=COMPLETED`. 이후 회원 4가 참여하면 409 `NOT_JOINABLE` | US1-2, FR-005 |
| 3 | 남은 수량 초과 | 남은 9인데 수량 10 | 409 `INSUFFICIENT_QUANTITY`, `remainingQuantity=9` | US1-3 |
| 4 | 재시도 | 회원 2가 수량 6으로 다시 참여 | 200, 변화 없음 | US1-4, FR-004 |
| 5 | 이미 참여, 다른 수량 | 회원 2가 수량 8로 참여 | 409 `ALREADY_JOINED` | US1-4 |
| 6 | 개설자 참여 | 회원 1이 참여 | 403 `ORGANIZER_NOT_ALLOWED` | US1-5, FR-010 |
| 7 | 로그인 없음 | 헤더 없음 | 401 | US1-6 |
| 8 | 가격 변경 | 개설자가 가격을 1,500으로 수정한 뒤 회원이 `expectedUnitPrice=1200`으로 참여 | 409 `PRICE_CHANGED`, `currentUnitPrice=1500` | US1-7, 결정 |
| 9 | 수량 줄이기 | 참여자(6)가 수량 3으로 변경 | 200, 확보 −3 | US4-2 |
| 10 | 수량 한도 | 참여자(6)·남은 9에서 16으로 변경 | 409 `INSUFFICIENT_QUANTITY` | US4-3 |
| 11 | 변경으로 완료 | 참여자(6)·남은 9에서 15로 변경 | 200, `COMPLETED` | US4-4 |
| 12 | 참여 취소 | 참여자(6)가 DELETE | 204, 확보 −6, 공구 `OPEN`. 이후 재참여 가능 | US3-1, US3-2 |
| 13 | 개설자 취소·변경 | 회원 1이 DELETE·PUT | 403 `ORGANIZER_NOT_ALLOWED` | US3-3, US4-5 |
| 14 | 완료·마감 후 | COMPLETED 공구에서 참여·변경·취소, 마감 경과 후 같은 요청 | 모두 409 `NOT_JOINABLE` | FR-009 |
| 15 | 마감 경계 | `Clock`을 마감 시각 정각으로 두고 참여 | 409 `NOT_JOINABLE` (마감 시각 ≤ 현재) | FR-009 |
| 16 | 마지막 자리 | 남은 1, 100명이 동시에 1개씩 | 201 정확히 1건, 나머지 409, `COMPLETED` 한 번, I1 위반 0 | US2-1, SC-002 |
| 17 | 다양한 수량 | 남은 10, 동시에 3·4·5·6 등 | 성공 수량 합 ≤ 10, `secured = 9 + 성공 합` | US2-2 |
| 18 | 이중 요청 | 같은 회원이 같은 요청 동시 2개 | 참여 행 1건, 수량이 같으면 둘 다 성공 응답 | US2-3, SC-006 |
| 19 | 취소와 경쟁 | 개설자 취소와 참여 동시 | 먼저 처리된 쪽 기준, 취소된 공구에 참여 행 없음 | US2-4, FR-011 |
| 20 | 이벤트 | 마지막 수량 참여 | `GroupBuyCompleted` 정확히 1건, 대상은 개설자 포함 전원. 일반 참여·변경·취소는 이벤트 없음 | FR-012 |

시나리오 16~19는 `QuantityRaceIT`(안전한 3방식), 나머지는 방식별 통합 테스트에서 돌린다. `none`은 정확성이 깨질 수 있어 테스트에서 제외하고 측정에서만 쓴다.

## 측정 (헌법 II)

방식 4개를 같은 시나리오로 돌려 비교한다. 결정 8의 고정 환경(Docker MySQL 8.4, 풀 크기 20)을 기록한다.

```bash
# 방식마다 앱을 다시 띄워서 같은 k6 시나리오를 실행 (benchmark 프로파일)
for gate in none pessimistic optimistic conditional; do
  ./gradlew bootRun --args="--spring.profiles.active=benchmark --camgong.quantity-gate=$gate" &
  # 시드 후
  k6 run load/k6/last-seat.js     # 남은 1, 100명
  k6 run load/k6/mixed.js         # 60초 혼합 부하
  # 불변식 검증 쿼리(data-model.md) 실행, 결과 기록
done
```

결과는 `docs/benchmarks/participation-concurrency.md`에 표(성공/거부 수, I1 위반 수, p50/p95/p99, 처리량, 재시도 수)와 해석으로 남긴다.

## 헌법 IV 필수 테스트와의 대응

| 필수 대상 | 시나리오 |
|-----------|----------|
| 정원(수량) 초과 판정 | 3, 10, 16, 17 |
| 모집 완료 전환 | 2, 11, 16, 20 |
| 동시 참여 경쟁 | 16, 17, 18, 19 |
