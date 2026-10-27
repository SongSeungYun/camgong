// 알림 목록 응답 시간(SC-006): 알림이 쌓인 회원이 알림함을 반복해서 연다. 사용: k6 run load/k6/notifications.js -e BASE_URL=…
// load/seed-notify.sql 로 알림 1,000건이 생긴 뒤(공구 취소 후) 회원 102~201 의 세션 쿠키로 요청한다.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 5,
  duration: __ENV.DURATION || '30s',
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: { http_req_duration: ['p(95)<1000'] },
};

export default function () {
  const member = 102 + Math.floor(Math.random() * 100);
  const headers = { headers: { Cookie: 'camgong_session=bench-session-' + member } };
  const list = http.get(`${BASE}/api/notifications`, headers);
  check(list, { 'list 200 with 1 item': (r) => r.status === 200 && r.json('items').length === 1 });
  const unread = http.get(`${BASE}/api/notifications/unread-count`, headers);
  check(unread, { 'unread 200': (r) => r.status === 200 });
}
