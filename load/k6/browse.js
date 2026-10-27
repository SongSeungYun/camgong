// 사용: k6 run load/k6/browse.js -e DATASET=100 [-e BASE_URL=http://localhost:8080]
// 목록 첫 페이지 세 가지(일반, 흔한 검색어, 드문 검색어)의 응답 시간을 잰다. SC-001(1만 건 p95 1초 이내), SC-007(사용자 결정 B, 2026-10-28: 절대 기준 p95 50ms).
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: Number(__ENV.VUS || 5),
  duration: '20s',
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
  thresholds: {
    'http_req_duration{name:list}': ['p(95)<50'],
    'http_req_duration{name:search_common}': ['p(95)<50'],
    'http_req_duration{name:search_rare}': ['p(95)<50'],
    'http_req_duration{name:list_deadline}': ['p(95)<50'],
  },
};

export default function () {
  const requests = [
    ['list', '/api/groupbuys'],
    ['list_deadline', '/api/groupbuys?sort=deadline'],
    ['search_common', '/api/groupbuys?q=' + encodeURIComponent('햇반')],
    ['search_rare', '/api/groupbuys?q=' + encodeURIComponent('희귀템')],
  ];
  for (const [name, path] of requests) {
    const res = http.get(BASE + path, { tags: { name } });
    check(res, { [name + ' 200']: (r) => r.status === 200 });
  }
}
