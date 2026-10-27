// 마감 확정이 도는 동안의 조회 영향(SC-007): 목록과 상세(로그인 쿠키)를 계속 읽는다.
// 사용: k6 run load/k6/list-during-close.js -e BASE_URL=… -e DURATION=60s -e ID_MIN=… -e ID_MAX=…   (load/seed.sql 의 세션 쿠키 사용)
// ID_MIN/ID_MAX 는 시드된 공구의 실제 id 범위다(AUTO_INCREMENT 는 DELETE 로 되돌아가지 않으므로 1..N 이 아닐 수 있다). 상세는 200 만 통과로 센다.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ID_MIN = Number(__ENV.ID_MIN || 1);
const ID_MAX = Number(__ENV.ID_MAX || 1000);

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
  const detail = http.get(`${BASE}/api/groupbuys/${ID_MIN + Math.floor(Math.random() * (ID_MAX - ID_MIN + 1))}`,
    { tags: { name: 'detail' }, headers: { Cookie: 'camgong_session=bench-session-2' } });
  check(detail, { 'detail 200': (r) => r.status === 200 });
}
