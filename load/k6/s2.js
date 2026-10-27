// 시나리오 S2: 남은 수량 10, 50명이 동시에 1~5개를 요청한다. 성공 수량 합은 10 을 넘으면 안 된다.
// 사용: load/reset.sql 을 @TOTAL=24, @ORG=14 로 실행한 뒤.
import { join, summaryTrendStats } from './common.js';

export const options = {
  scenarios: { s2: { executor: 'per-vu-iterations', vus: 50, iterations: 1, maxDuration: '30s' } },
  summaryTrendStats,
};

export default function () {
  join(__VU + 1, ((__VU * 7) % 5) + 1);     // 수량은 VU 번호로 정해 방식끼리 같은 요청을 받게 한다
}
