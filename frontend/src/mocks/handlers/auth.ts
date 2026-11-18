import { http, HttpResponse } from 'msw'
import { chars, hasInvisible, reservedKey, strip } from '../text'
import { NOT_UNIVERSITY, schoolOf } from '../school'
import { currentMember, mockState } from '../state'
import { shownStatus } from './groupbuys'

const problem = (status: number, title: string, extra: Record<string, unknown> = {}, headers?: HeadersInit) =>
  HttpResponse.json({ title, status, ...extra }, { status, headers })

const toMember = (m: { id: number; displayName: string; school: string }) => ({ member: { id: m.id, displayName: m.displayName, school: m.school } })
const RESEND_MS = 60_000
export const WITHDRAWN_NAME = '탈퇴한 회원'
const locked = () => mockState.loginLocked || mockState.loginFailures >= 5
const lockedRes = () => problem(429, 'LOGIN_LOCKED', { retryAt: isoKst(Date.now() + 15 * 60_000) }, { 'Retry-After': '900' })
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
    if (locked()) return lockedRes()
    const m = mockState.members.find((x) => !x.withdrawn && x.email === email.trim().toLowerCase()) // 탈퇴 계정은 없는 계정과 같다(010)
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
    if (!/^[^@\s]+@[^@\s]+$/.test(email) || !schoolOf(email)) return problem(400, 'Bad Request', { errors: [{ field: 'email', message: NOT_UNIVERSITY }] })
    if (mockState.members.some((m) => m.email === email)) return problem(409, 'ALREADY_REGISTERED')

    const prev = mockState.pending.find((p) => p.email === email)
    if (prev && Date.now() - prev.lastSentAt < RESEND_MS) return tooMany(prev.lastSentAt + RESEND_MS)
    const next = { email, token: `tok-${mockState.nextToken++}`, lastSentAt: Date.now() } // 재신청도 새 토큰(옛 토큰 무효, 002)
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
    p.token = `tok-${mockState.nextToken++}` // 재발송도 새 토큰(백엔드와 같다)
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
    // 009: 확인 시점에 다시 판정한다(그 사이 추가 목록에서 빠졌으면 token 필드 400). 서버처럼 토큰 검사 바로 다음, 본문 검증보다 먼저
    const school = schoolOf(p.email)
    if (!school) return problem(400, 'Bad Request', { errors: [{ field: 'token', message: '이 메일로는 더 이상 가입할 수 없습니다.' }] })

    const name = typeof b.displayName === 'string' ? strip(b.displayName) : ''
    const consent = b.consent === 'true' ? true : b.consent === 'false' ? false : b.consent // 서버는 문자열 "true"/"false"도 불리언으로 받는다
    const password = typeof b.password === 'string' ? b.password : ''
    const errors: { field: string; message: string }[] = []
    if (chars(name) < 2 || chars(name) > 20) errors.push({ field: 'displayName', message: '2~20자로 입력해 주세요' })
    else if (reservedKey(name) === '탈퇴한회원') errors.push({ field: 'displayName', message: '쓸 수 없는 이름입니다.' }) // 010 예약어(서버처럼 보이지 않는 글자 검사보다 먼저)
    else if (hasInvisible(name)) errors.push({ field: 'displayName', message: '보이지 않는 글자는 쓸 수 없어요' })
    if (password.length < 8) errors.push({ field: 'password', message: '8자 이상 입력해 주세요' })
    else if (!password.trim()) errors.push({ field: 'password', message: '공백만으로는 쓸 수 없어요' }) // 서버 isBlank
    else if (new TextEncoder().encode(password).length > 72) errors.push({ field: 'password', message: '72바이트 이하로 입력해 주세요' })
    if (consent !== true) errors.push({ field: 'consent', message: '동의가 필요해요' })
    if (typeof b.consentVersion !== 'string' || !b.consentVersion) errors.push({ field: 'consentVersion', message: '동의 안내문 버전이 필요해요' })
    if (errors.length) return problem(400, 'Bad Request', { errors })

    if (mockState.members.some((m) => m.displayName === name)) return problem(409, 'DISPLAY_NAME_TAKEN')
    const member = { id: mockState.nextMemberId++, displayName: name, email: p.email, password, school }
    mockState.members.push(member)
    mockState.pending = mockState.pending.filter((x) => x !== p)
    mockState.sessionMemberId = member.id
    return HttpResponse.json(toMember(member))
  }),

  // 010 탈퇴: 401 → 400(비밀번호 누락) → 429(잠금) → 400 INVALID_PASSWORD(실패 횟수 증가) → 409 ACTIVE_GROUPBUY → 204
  http.post('/api/auth/withdraw', async ({ request }) => {
    const me = currentMember()
    if (!me) return problem(401, 'UNAUTHENTICATED')
    const b = (await request.json().catch(() => ({}))) as { password?: unknown }
    if (typeof b.password !== 'string' || !b.password.trim()) return problem(400, 'Bad Request', { errors: [{ field: 'password', message: '비밀번호를 입력해 주세요' }] })
    if (locked()) return lockedRes()
    if (b.password !== me.password) {
      mockState.loginFailures++
      return problem(400, 'INVALID_PASSWORD', { errors: [{ field: 'password', message: '비밀번호가 맞지 않습니다.' }] })
    }
    const blockers = mockState.groupBuys
      .filter((g) => ['OPEN', 'PENDING_CLOSE'].includes(shownStatus(g)) && (g.organizerId === me.id || g.participations.some((p) => p.memberId === me.id)))
      .sort((a, b) => a.id - b.id)
      .map((g) => ({ groupBuyId: g.id, title: g.title, role: g.organizerId === me.id ? 'ORGANIZER' : 'PARTICIPANT', status: shownStatus(g) }))
    if (blockers.length) return problem(409, 'ACTIVE_GROUPBUY', { detail: '진행 중인 공구가 있어 탈퇴할 수 없습니다.', blockers })
    mockState.notifications = mockState.notifications.filter((n) => n.memberId !== me.id)
    mockState.pending = mockState.pending.filter((p) => p.email !== me.email)
    Object.assign(me, { withdrawn: true, displayName: WITHDRAWN_NAME, email: '', password: '' })
    mockState.loginFailures = 0 // 같은 이메일의 로그인 실패 기록도 지운다(plan 5)
    mockState.sessionMemberId = null
    return new HttpResponse(null, { status: 204 })
  }),
]
