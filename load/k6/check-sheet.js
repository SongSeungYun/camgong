// 체크표 응답 시간(SC-002): 개설자가 참여자 50명 공구의 체크표를 반복해서 연다. 사용: k6 run load/k6/check-sheet.js -e BASE_URL=… -e GROUPBUY_ID=…
// 쿠키는 load/seed.sql 의 개설자 세션(회원 1)이다.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ID = __ENV.GROUPBUY_ID || '1';

export const options = {
  vus: 5,
  duration: __ENV.DURATION || '30s',
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: { http_req_duration: ['p(95)<1000'] },
};

export default function () {
  const res = http.get(`${BASE}/api/groupbuys/${ID}/check-sheet`, { headers: { Cookie: 'camgong_session=bench-session-1' } });
  check(res, { '200 and 50 rows': (r) => r.status === 200 && r.json('rows').length === 50 });
}
