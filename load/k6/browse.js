// 사용: k6 run load/k6/browse.js -e DATASET=100 [-e BASE_URL=http://localhost:8080]
// 목록 첫 페이지 네 가지(최신순, 마감 임박순, 흔한 검색어, 드문 검색어)의 응답 시간을 잰다. SC-001(1만 건 p95 1초 이내), SC-007(사용자 결정 B, 2026-10-28: 절대 기준 p95 50ms).
// 같은 네 가지를 비로그인(전국)과 로그인 회원(자기 학교로 거름, 009 SC-004)으로 잰다. 로그인 쿠키는 load/seed-browse.sql 의 회원 1 세션이다.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const MEMBER_SCHOOL = __ENV.MEMBER_SCHOOL || 's01.ac.kr';

export const options = {
  vus: Number(__ENV.VUS || 5),
  duration: '20s',
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
  thresholds: {
    'http_req_duration{name:list}': ['p(95)<50'],
    'http_req_duration{name:search_common}': ['p(95)<50'],
    'http_req_duration{name:search_rare}': ['p(95)<50'],
    'http_req_duration{name:list_deadline}': ['p(95)<50'],
    'http_req_duration{name:member_list}': ['p(95)<50'],
    'http_req_duration{name:member_search_common}': ['p(95)<50'],
    'http_req_duration{name:member_search_rare}': ['p(95)<50'],
    'http_req_duration{name:member_list_deadline}': ['p(95)<50'],
  },
};

export default function () {
  const requests = [
    ['list', '/api/groupbuys'],
    ['list_deadline', '/api/groupbuys?sort=deadline'],
    ['search_common', '/api/groupbuys?q=' + encodeURIComponent('햇반')],
    ['search_rare', '/api/groupbuys?q=' + encodeURIComponent('희귀템')],
  ];
  const member = { cookies: { camgong_session: __ENV.SESSION || 'bench-session-1' } };
  for (const [name, path] of requests) {
    const res = http.get(BASE + path, { tags: { name } });
    check(res, { [name + ' 200']: (r) => r.status === 200 });
    const mine = http.get(BASE + path, Object.assign({ tags: { name: 'member_' + name } }, member));
    check(mine, {
      ['member_' + name + ' 200']: (r) => r.status === 200,
      ['member_' + name + ' 자기 학교만']: (r) => r.status === 200 && r.json('items').every((i) => i.school === MEMBER_SCHOOL),
    });
  }
}
