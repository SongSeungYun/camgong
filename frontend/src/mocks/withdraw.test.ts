import { beforeEach, expect, test } from 'vitest'
import { ApiError, get, post } from '../shared/api/client'
import { makeGroupBuy } from '../test/factories'
import { mockState } from './state'

// 010 계약(specs/010-withdraw/contracts/withdraw-api.md)대로 목이 응답하는지
beforeEach(() => mockState.reset())

const err = (p: Promise<unknown>) => p.then(() => { throw new Error('거부될 것으로 기대했지만 성공했다') }, (e: ApiError) => e)
const withdraw = (password?: unknown) => post('/api/auth/withdraw', password === undefined ? {} : { password })
const past = () => new Date(Date.now() - 60_000).toISOString()

test('비로그인 401, 비밀번호 누락·빈 값 400(errors password)', async () => {
  expect(await err(withdraw('password1234'))).toMatchObject({ status: 401, code: 'UNAUTHENTICATED' })
  mockState.sessionMemberId = 3
  mockState.groupBuys = []
  for (const p of [undefined, '']) expect(await err(withdraw(p))).toMatchObject({ status: 400, fieldErrors: [{ field: 'password' }] })
})

test('비밀번호가 틀리면 400 INVALID_PASSWORD이고 아무것도 바뀌지 않으며, 5회면 429 LOGIN_LOCKED(로그인도 잠김)', async () => {
  mockState.sessionMemberId = 3
  mockState.groupBuys = []
  const e = await err(withdraw('wrong-password'))
  expect(e).toMatchObject({ status: 400, code: 'INVALID_PASSWORD', fieldErrors: [{ field: 'password', message: '비밀번호가 맞지 않습니다.' }] })
  expect(mockState.members.find((m) => m.id === 3)!.displayName).toBe('구경꾼')
  for (let i = 0; i < 4; i++) await err(withdraw('wrong-password'))
  const locked = await err(withdraw('password1234'))
  expect(locked).toMatchObject({ status: 429, code: 'LOGIN_LOCKED' })
  expect(typeof locked.extra.retryAt).toBe('string')
  await post('/api/auth/logout')
  expect(await err(post('/api/auth/login', { email: 'outsider@school.ac.kr', password: 'password1234' }))).toMatchObject({ status: 429 })
})

test('진행 중 공구가 있으면 409 ACTIVE_GROUPBUY + blockers(역할, 표시 상태, id 오름차순), 아무것도 바뀌지 않는다', async () => {
  mockState.groupBuys = [
    makeGroupBuy({ id: 7, organizerId: 1, participations: [{ memberId: 1, quantity: 1 }, { memberId: 3, quantity: 1 }], deadline: past(), title: '휴지 공구' }),
    makeGroupBuy({ id: 3, organizerId: 3, participations: [{ memberId: 3, quantity: 1 }], title: '햇반 24개 공구' }),
    makeGroupBuy({ id: 5, organizerId: 1, status: 'COMPLETED', participations: [{ memberId: 1, quantity: 1 }, { memberId: 3, quantity: 23 }] }),
    makeGroupBuy({ id: 9, organizerId: 3, status: 'FAILED', participations: [{ memberId: 3, quantity: 1 }] }),
  ]
  mockState.sessionMemberId = 3
  const e = await err(withdraw('password1234'))
  expect(e).toMatchObject({ status: 409, code: 'ACTIVE_GROUPBUY' })
  expect(e.extra.blockers).toEqual([
    { groupBuyId: 3, title: '햇반 24개 공구', role: 'ORGANIZER', status: 'OPEN' },
    { groupBuyId: 7, title: '휴지 공구', role: 'PARTICIPANT', status: 'PENDING_CLOSE' },
  ])
  expect(await get('/api/auth/me')).toMatchObject({ member: { id: 3 } })
})

test('성공 204: 로그아웃, 이름은 "탈퇴한 회원", 로그인은 없는 계정과 같은 401, 알림 삭제, 같은 이메일·옛 이름으로 재가입', async () => {
  mockState.groupBuys = [makeGroupBuy({ status: 'COMPLETED', participations: [{ memberId: 1, quantity: 3 }, { memberId: 3, quantity: 21 }] })]
  mockState.notifications = [{ id: 1, memberId: 3, type: 'GROUPBUY_COMPLETED', groupBuyId: 1, groupBuyTitle: 't', changedFields: null, createdAt: past(), read: false, linkable: true }]
  mockState.sessionMemberId = 3
  expect(await withdraw('password1234')).toBeUndefined()
  expect(await err(get('/api/auth/me'))).toMatchObject({ status: 401 })
  expect(mockState.notifications).toHaveLength(0)
  expect(await err(post('/api/auth/login', { email: 'outsider@school.ac.kr', password: 'password1234' }))).toMatchObject({ status: 401, code: 'INVALID_CREDENTIALS' })
  mockState.loginFailures = 0
  mockState.sessionMemberId = 1 // 개설자: 체크표에 "탈퇴한 회원" 줄이 수량 그대로
  const sheet = await get<{ rows: { name: string; quantity: number }[] }>('/api/groupbuys/1/check-sheet')
  expect(sheet.rows).toEqual([expect.objectContaining({ name: '탈퇴한 회원', quantity: 21 })])
  await post('/api/auth/logout')
  await post('/api/auth/signup', { email: 'outsider@school.ac.kr' })
  const token = mockState.pending.find((p) => p.email === 'outsider@school.ac.kr')!.token
  expect(await post('/api/auth/verify', { token, displayName: '구경꾼', password: 'password1234', consent: true, consentVersion: 'v1' })).toMatchObject({ member: { displayName: '구경꾼' } })
})

test('가입 인증에서 표시 이름 "탈퇴한 회원"은 400 errors[displayName]', async () => {
  await post('/api/auth/signup', { email: 'n@school.ac.kr' })
  const token = mockState.pending[0]!.token
  // 계약 cab0cdd: 모든 공백(유니코드·가운데 포함)을 지운 값이 '탈퇴한회원'이면 거부
  for (const displayName of ['탈퇴한 회원', '  탈퇴한 회원 ', '탈퇴한회원', '탈퇴한  회원', '탈퇴한\u3000회원', '탈퇴한\u00a0회원']) {
    expect(await err(post('/api/auth/verify', { token, displayName, password: 'password1234', consent: true, consentVersion: 'v1' }))).toMatchObject({
      status: 400,
      fieldErrors: [{ field: 'displayName', message: '쓸 수 없는 이름입니다.' }],
    })
  }
})

test('리뷰 4·5: 공백뿐인 비밀번호는 누락과 같은 400(횟수 안 늘림), 성공하면 로그인 실패 횟수가 지워진다', async () => {
  mockState.sessionMemberId = 3
  mockState.groupBuys = []
  expect(await err(withdraw('   '))).toMatchObject({ status: 400, code: 'Bad Request', fieldErrors: [{ field: 'password' }] })
  expect(mockState.loginFailures).toBe(0)
  await err(withdraw('wrong-password'))
  await err(withdraw('wrong-password'))
  await withdraw('password1234')
  expect(mockState.loginFailures).toBe(0)
})

test('검수 7: 공백뿐인 비밀번호로는 가입(인증)할 수 없다', async () => {
  await post('/api/auth/signup', { email: 'b@school.ac.kr' })
  const token = mockState.pending[0]!.token
  expect(await err(post('/api/auth/verify', { token, displayName: '공백비번', password: '        ', consent: true, consentVersion: 'v1' }))).toMatchObject({
    status: 400,
    fieldErrors: [{ field: 'password' }],
  })
})
