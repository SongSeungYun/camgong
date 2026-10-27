import http from 'k6/http';
import { Counter } from 'k6/metrics';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080';
export const GROUPBUY = __ENV.GROUPBUY_ID || '1';

// 응답을 상태 코드(와 409 의 title)별로 센다. 동적 이름은 init 단계에서 만들어야 해서 미리 정해 둔다.
const KEYS = ['r200', 'r201', 'r204', 'r400', 'r401', 'r403', 'r404', 'r409_NOT_JOINABLE', 'r409_INSUFFICIENT_QUANTITY',
  'r409_BUSY', 'r409_PRICE_CHANGED', 'r409_ALREADY_JOINED', 'r409_other', 'r5xx', 'rother'];
export const counters = {};
for (const k of KEYS) counters[k] = new Counter(k);

export function record(res) {
  let key;
  if (res.status === 409) {
    let title = 'other';
    try { title = res.json('title'); } catch (e) { /* 본문이 JSON 이 아님 */ }
    key = counters['r409_' + title] ? 'r409_' + title : 'r409_other';
  } else if (res.status >= 500) {
    key = 'r5xx';
  } else {
    key = counters['r' + res.status] ? 'r' + res.status : 'rother';
  }
  counters[key].add(1);
}

function headers(memberId) {
  return { headers: { 'Content-Type': 'application/json', Cookie: 'camgong_session=bench-session-' + memberId } };
}

export function join(memberId, quantity) {
  const res = http.post(`${BASE}/api/groupbuys/${GROUPBUY}/participations`,
    JSON.stringify({ quantity, expectedUnitPrice: 1200 }), headers(memberId));
  record(res);
  return res;
}

export function change(memberId, quantity) {
  const res = http.put(`${BASE}/api/groupbuys/${GROUPBUY}/participations/me`,
    JSON.stringify({ quantity, expectedUnitPrice: 1200 }), headers(memberId));
  record(res);
  return res;
}

export function leave(memberId) {
  const res = http.del(`${BASE}/api/groupbuys/${GROUPBUY}/participations/me`, null, headers(memberId));
  record(res);
  return res;
}

export function updateGroupBuy(organizerQuantity, title) {
  const body = {
    title, description: null, itemName: '햇반', unitPrice: 1200, totalQuantity: __ENV.TOTAL ? Number(__ENV.TOTAL) : 24,
    deadline: new Date(Date.now() + 7 * 86400 * 1000).toISOString(), organizerQuantity, category: 'FOOD',
    itemUrl: 'https://example.com/item', openChatUrl: 'https://open.kakao.com/o/abc',
  };
  const res = http.put(`${BASE}/api/groupbuys/${GROUPBUY}`, JSON.stringify(body), headers(1));
  record(res);
  return res;
}

export const summaryTrendStats = ['avg', 'med', 'p(95)', 'p(99)', 'max'];
