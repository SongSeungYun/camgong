// 시나리오 S1(마지막 자리): 남은 수량 1, 서로 다른 100명이 동시에 1개씩. 성공은 정확히 1건이어야 한다.
// 사용: load/reset.sql 을 @TOTAL=24, @ORG=23 으로 실행한 뒤 k6 run load/k6/last-seat.js -e BASE_URL=…
import { join, summaryTrendStats } from './common.js';

export const options = {
  scenarios: { lastSeat: { executor: 'per-vu-iterations', vus: 100, iterations: 1, maxDuration: '30s' } },
  summaryTrendStats,
};

export default function () {
  join(__VU + 1, 1);          // VU 1..100 → 회원 2..101
}
