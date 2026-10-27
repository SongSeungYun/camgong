// 혼합 부하: 회원 2~21(20 VU)이 참여·수량 변경·참여 취소를 무작위로 하고, 개설자가 공구를 수정한다.
// 사용: load/reset.sql 을 @TOTAL=24, @ORG=9 로 실행한 뒤 k6 run load/k6/mixed.js -e DURATION=30s
import { join, change, leave, updateGroupBuy, summaryTrendStats } from './common.js';
import { sleep } from 'k6';

export const options = {
  scenarios: {
    members: { executor: 'constant-vus', vus: 20, duration: __ENV.DURATION || '30s', exec: 'member' },
    organizer: { executor: 'constant-vus', vus: 1, duration: __ENV.DURATION || '30s', exec: 'organizer' },
  },
  summaryTrendStats,
};

let joined = false;          // VU 마다 따로 가진다

export function member() {
  const me = __VU + 1;       // 회원 VU 1..20 → 회원 2..21 (개설자 VU 는 다른 시나리오)
  const roll = Math.random();
  if (!joined) {
    const res = join(me, 1 + Math.floor(Math.random() * 3));
    joined = res.status === 201 || res.status === 200;
  } else if (roll < 0.5) {
    change(me, 1 + Math.floor(Math.random() * 3));
  } else {
    const res = leave(me);
    if (res.status === 204 || res.status === 404) joined = false;
  }
}

export function organizer() {
  updateGroupBuy(9 + Math.floor(Math.random() * 2), '측정용 공구');
  sleep(0.2);
}
