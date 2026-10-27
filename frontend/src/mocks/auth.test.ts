import { beforeEach, expect, test } from 'vitest'
import { ApiError, get, post } from '../shared/api/client'
import { mockState } from './state'

beforeEach(() => mockState.reset())

const err = async (p: Promise<unknown>): Promise<ApiError> => {
  try {
    await p
  } catch (e) {
    return e as ApiError
  }
  throw new Error('거부될 것으로 기대했지만 성공했다')
}

test('목 인증이 계약 모양으로 응답한다', async () => {
  expect(await err(get('/api/auth/me'))).toMatchObject({ status: 401, code: 'UNAUTHENTICATED' })
  expect(await err(post('/api/auth/login', { email: 'organizer@school.ac.kr', password: 'x' }))).toMatchObject({ status: 401, code: 'INVALID_CREDENTIALS' })
  expect(await post('/api/auth/login', { email: 'organizer@school.ac.kr', password: 'password1234' })).toEqual({ member: { id: 1, displayName: '개설자' } })
  expect(await get('/api/auth/me')).toEqual({ member: { id: 1, displayName: '개설자' } })
  expect(await post('/api/auth/logout')).toBeUndefined()
  expect(await err(get('/api/auth/me'))).toMatchObject({ status: 401 })
})

test('5회 실패하면 LOGIN_LOCKED + retryAt', async () => {
  for (let i = 0; i < 5; i++) await err(post('/api/auth/login', { email: 'a@school.ac.kr', password: 'x' }))
  const e = await err(post('/api/auth/login', { email: 'organizer@school.ac.kr', password: 'password1234' }))
  expect(e).toMatchObject({ status: 429, code: 'LOGIN_LOCKED' })
  expect(typeof e.extra.retryAt).toBe('string')
})

test('가입 → 재발송 한도 → 인증 → 로그인된 상태', async () => {
  const body = { email: 'new@school.ac.kr', displayName: '새내기', password: 'password1234', consent: true, consentVersion: 'v1' }
  expect(await err(post('/api/auth/signup', { ...body, password: 'short' }))).toMatchObject({ status: 400, fieldErrors: [{ field: 'password' }] })
  expect(await post('/api/auth/signup', body)).toMatchObject({ email: 'new@school.ac.kr' })
  expect(await err(post('/api/auth/signup/resend', { email: body.email }))).toMatchObject({ status: 429, code: 'TOO_MANY_REQUESTS' })
  expect(await err(post('/api/auth/login', { email: body.email, password: body.password }))).toMatchObject({ status: 403, code: 'EMAIL_NOT_VERIFIED' })
  expect(await err(post('/api/auth/verify', { token: 'nope' }))).toMatchObject({ status: 400, code: 'INVALID_TOKEN' })
  expect(await err(post('/api/auth/verify', { token: 'expired-token' }))).toMatchObject({ status: 410, code: 'TOKEN_EXPIRED' })
  expect(await post('/api/auth/verify', { token: mockState.pending[0]!.token })).toEqual({ member: { id: 4, displayName: '새내기' } })
  expect(await get('/api/auth/me')).toEqual({ member: { id: 4, displayName: '새내기' } })
})

test('인증 시 표시 이름 충돌이면 DISPLAY_NAME_TAKEN, 새 이름으로 재시도하면 성공', async () => {
  await post('/api/auth/signup', { email: 'n2@school.ac.kr', displayName: '개설자', password: 'password1234', consent: true, consentVersion: 'v1' })
  const token = mockState.pending[0]!.token
  expect(await err(post('/api/auth/verify', { token }))).toMatchObject({ status: 409, code: 'DISPLAY_NAME_TAKEN' })
  expect(await post('/api/auth/verify', { token, displayName: '다른이름' })).toMatchObject({ member: { displayName: '다른이름' } })
})

test('시드: 공구 25개 이상, 상태 4종·카테고리 6종, 마감 임박 포함', () => {
  const g = mockState.groupBuys
  expect(g.length).toBeGreaterThanOrEqual(25)
  expect(new Set(g.map((x) => x.status)).size).toBe(4)
  expect(new Set(g.map((x) => x.category)).size).toBe(6)
  expect(g.some((x) => x.status === 'OPEN' && Date.parse(x.deadline) - Date.now() < 24 * 3_600_000)).toBe(true)
})
