import { http, HttpResponse } from 'msw'
import { currentMember, mockState } from '../state'

const problem = (status: number, title: string, extra: Record<string, unknown> = {}, headers?: HeadersInit) =>
  HttpResponse.json({ title, status, ...extra }, { status, headers })

const toMember = (m: { id: number; displayName: string }) => ({ member: { id: m.id, displayName: m.displayName } })
const RESEND_MS = 60_000
const isoKst = (ms: number) => new Date(ms).toISOString()
const tooMany = (retryAtMs: number) =>
  problem(429, 'TOO_MANY_REQUESTS', { retryAt: isoKst(retryAtMs) }, { 'Retry-After': String(Math.ceil((retryAtMs - Date.now()) / 1000)) })

export const authHandlers = [
  http.get('/api/auth/me', () => {
    const m = currentMember()
    return m ? HttpResponse.json(toMember(m)) : problem(401, 'UNAUTHENTICATED')
  }),

  http.post('/api/auth/login', async ({ request }) => {
    const { email, password } = (await request.json()) as { email: string; password: string }
    if (mockState.loginLocked || mockState.loginFailures >= 5) {
      const at = Date.now() + 15 * 60_000
      return problem(429, 'LOGIN_LOCKED', { retryAt: isoKst(at) }, { 'Retry-After': '900' })
    }
    const m = mockState.members.find((x) => x.email === email.trim().toLowerCase())
    if (m && m.password === password) {
      mockState.loginFailures = 0
      mockState.sessionMemberId = m.id
      mockState.sessionExpired = false
      return HttpResponse.json(toMember(m))
    }
    // ui-api-map.md는 인증 전 계정에 403 EMAIL_NOT_VERIFIED를 쓰지만 002 계약은 401과 같이 취급할지 '결정 대기'다.
    // 아직 바뀌지 않은 쪽(ui-api-map)을 따른다.
    if (!m && mockState.pending.some((p) => p.email === email.trim().toLowerCase() && p.password === password)) {
      return problem(403, 'EMAIL_NOT_VERIFIED')
    }
    mockState.loginFailures++
    return problem(401, 'INVALID_CREDENTIALS')
  }),

  http.post('/api/auth/logout', () => {
    mockState.sessionMemberId = null
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('/api/auth/signup', async ({ request }) => {
    const b = (await request.json()) as Record<string, unknown>
    const errors: { field: string; message: string }[] = []
    const email = String(b.email ?? '').trim().toLowerCase()
    const displayName = String(b.displayName ?? '').trim()
    const password = String(b.password ?? '')
    if (!/^[^@\s]+@school\.ac\.kr$/.test(email)) errors.push({ field: 'email', message: '허용된 학교 이메일이 아니에요' })
    if (displayName.length < 2 || displayName.length > 20) errors.push({ field: 'displayName', message: '2~20자로 입력해 주세요' })
    if (password.length < 8) errors.push({ field: 'password', message: '8자 이상 입력해 주세요' })
    if (b.consent !== true) errors.push({ field: 'consent', message: '동의가 필요해요' })
    if (errors.length) return problem(400, 'VALIDATION_FAILED', { errors })
    if (mockState.members.some((m) => m.email === email)) return problem(409, 'ALREADY_REGISTERED')

    const prev = mockState.pending.find((p) => p.email === email)
    if (prev && Date.now() - prev.lastSentAt < RESEND_MS) return tooMany(prev.lastSentAt + RESEND_MS)
    const next = { email, displayName, password, token: prev?.token ?? `tok-${mockState.nextToken++}`, lastSentAt: Date.now() }
    mockState.pending = [...mockState.pending.filter((p) => p.email !== email), next]
    return HttpResponse.json({ email, resendAvailableAt: isoKst(next.lastSentAt + RESEND_MS) }, { status: 202 })
  }),

  http.post('/api/auth/signup/resend', async ({ request }) => {
    const { email } = (await request.json()) as { email?: string }
    if (!email || !/^[^@\s]+@[^@\s]+$/.test(email)) {
      return problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'email', message: '이메일 형식이 아니에요' }] })
    }
    const p = mockState.pending.find((x) => x.email === email.trim().toLowerCase())
    if (!p) return problem(404, 'NOT_FOUND')
    if (Date.now() - p.lastSentAt < RESEND_MS) return tooMany(p.lastSentAt + RESEND_MS)
    p.lastSentAt = Date.now()
    return HttpResponse.json({ email: p.email, resendAvailableAt: isoKst(p.lastSentAt + RESEND_MS) }, { status: 202 })
  }),

  // 토큰 `expired-token`·`used-token`은 시나리오용 고정 값이다.
  http.post('/api/auth/verify', async ({ request }) => {
    const { token, displayName } = (await request.json()) as { token: string; displayName?: string }
    if (token === 'expired-token') return problem(410, 'TOKEN_EXPIRED')
    if (token === 'used-token') return problem(409, 'TOKEN_USED')
    const p = mockState.pending.find((x) => x.token === token)
    if (!p) return problem(400, 'INVALID_TOKEN')
    const name = (displayName ?? p.displayName).trim()
    if (mockState.members.some((m) => m.displayName === name)) return problem(409, 'DISPLAY_NAME_TAKEN')
    const member = { id: mockState.nextMemberId++, displayName: name, email: p.email, password: p.password }
    mockState.members.push(member)
    mockState.pending = mockState.pending.filter((x) => x !== p)
    mockState.sessionMemberId = member.id
    return HttpResponse.json(toMember(member))
  }),
]
