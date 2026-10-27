// 마감 확정이 도는 동안의 조회 영향(SC-007): 목록과 상세(로그인 쿠키)를 계속 읽는다.
// 사용: k6 run load/k6/list-during-close.js -e BASE_URL=… -e DURATION=60s   (load/seed.sql 의 세션 쿠키 사용)
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 5,
  duration: __ENV.DURATION || '60s',
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: { 'http_req_duration{name:list}': ['p(95)<1000'], 'http_req_duration{name:detail}': ['p(95)<1000'] },
};

export default function () {
  const list = http.get(`${BASE}/api/groupbuys?status=OPEN`, { tags: { name: 'list' } });
  check(list, { 'list 200': (r) => r.status === 200 });
  const failed = http.get(`${BASE}/api/groupbuys?status=FAILED&size=50`, { tags: { name: 'list' } });
  check(failed, { 'failed list 200': (r) => r.status === 200 });
  const detail = http.get(`${BASE}/api/groupbuys/${1 + Math.floor(Math.random() * 1000)}`,
    { tags: { name: 'detail' }, headers: { Cookie: 'camgong_session=bench-session-2' } });
  check(detail, { 'detail 200 or 404': (r) => r.status === 200 || r.status === 404 });
}
