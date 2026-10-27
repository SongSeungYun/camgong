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
    // 인증 전 계정도 같은 401 INVALID_CREDENTIALS다(002 확정 2026-10-27, EMAIL_NOT_VERIFIED 없음).
    mockState.loginFailures++
    return problem(401, 'INVALID_CREDENTIALS')
  }),

  http.post('/api/auth/logout', () => {
    mockState.sessionMemberId = null
    return new HttpResponse(null, { status: 204 })
  }),

  // 변경 B2: 신청은 이메일만 받는다(이름·비밀번호·동의는 verify에서)
  http.post('/api/auth/signup', async ({ request }) => {
    const b = (await request.json()) as Record<string, unknown>
    const email = String(b.email ?? '').trim().toLowerCase()
    if (!/^[^@\s]+@school\.ac\.kr$/.test(email)) return problem(400, 'Bad Request', { errors: [{ field: 'email', message: '허용된 학교 이메일이 아니에요' }] })
    if (mockState.members.some((m) => m.email === email)) return problem(409, 'ALREADY_REGISTERED')

    const prev = mockState.pending.find((p) => p.email === email)
    if (prev && Date.now() - prev.lastSentAt < RESEND_MS) return tooMany(prev.lastSentAt + RESEND_MS)
    const next = { email, token: prev?.token ?? `tok-${mockState.nextToken++}`, lastSentAt: Date.now() }
    mockState.pending = [...mockState.pending.filter((p) => p.email !== email), next]
    return HttpResponse.json({ email, resendAvailableAt: isoKst(next.lastSentAt + RESEND_MS) }, { status: 202 })
  }),

  http.post('/api/auth/signup/resend', async ({ request }) => {
    const { email } = (await request.json()) as { email?: string }
    if (!email || !/^[^@\s]+@[^@\s]+$/.test(email)) {
      return problem(400, 'Bad Request', { errors: [{ field: 'email', message: '이메일 형식이 아니에요' }] })
    }
    const p = mockState.pending.find((x) => x.email === email.trim().toLowerCase())
    if (!p) return problem(404, 'NOT_FOUND')
    if (Date.now() - p.lastSentAt < RESEND_MS) return tooMany(p.lastSentAt + RESEND_MS)
    p.lastSentAt = Date.now()
    return HttpResponse.json({ email: p.email, resendAvailableAt: isoKst(p.lastSentAt + RESEND_MS) }, { status: 202 })
  }),

  // 토큰 `expired-token`·`used-token`은 시나리오용 고정 값이다.
  // 002 처리 순서: 토큰(400/409/410) → 본문 검증(400, 토큰 미소진) → 이름·이메일 충돌(409, 토큰 유효) → 성공
  http.post('/api/auth/verify', async ({ request }) => {
    const b = (await request.json()) as Record<string, unknown>
    const token = String(b.token ?? '')
    if (token === 'expired-token') return problem(410, 'TOKEN_EXPIRED')
    if (token === 'used-token') return problem(409, 'TOKEN_USED')
    const p = mockState.pending.find((x) => x.token === token)
    if (!p) return problem(400, 'INVALID_TOKEN')

    const name = typeof b.displayName === 'string' ? b.displayName.trim() : ''
    const password = typeof b.password === 'string' ? b.password : ''
    const errors: { field: string; message: string }[] = []
    if (name.length < 2 || name.length > 20) errors.push({ field: 'displayName', message: '2~20자로 입력해 주세요' })
    if (password.length < 8) errors.push({ field: 'password', message: '8자 이상 입력해 주세요' })
    else if (new TextEncoder().encode(password).length > 72) errors.push({ field: 'password', message: '72바이트 이하로 입력해 주세요' })
    if (b.consent !== true) errors.push({ field: 'consent', message: '동의가 필요해요' })
    if (typeof b.consentVersion !== 'string' || !b.consentVersion) errors.push({ field: 'consentVersion', message: '동의 안내문 버전이 필요해요' })
    if (errors.length) return problem(400, 'Bad Request', { errors })

    if (mockState.members.some((m) => m.displayName === name)) return problem(409, 'DISPLAY_NAME_TAKEN')
    const member = { id: mockState.nextMemberId++, displayName: name, email: p.email, password }
    mockState.members.push(member)
    mockState.pending = mockState.pending.filter((x) => x !== p)
    mockState.sessionMemberId = member.id
    return HttpResponse.json(toMember(member))
  }),
]
