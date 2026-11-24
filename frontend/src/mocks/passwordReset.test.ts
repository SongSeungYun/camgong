import { describe, expect, test } from 'vitest'
import { ApiError } from '../shared/api/client'
import { requestPasswordReset, confirmPasswordReset } from '../features/auth/api'
import { mockState } from './state'

// 011 T010: 목이 계약(contracts/password-reset-api.md) 그대로인지
const fail = (p: Promise<unknown>) => p.then(() => { throw new Error('성공하면 안 된다') }, (e: ApiError) => e)
const tokenOf = (email: string) => mockState.passwordResets.find((r) => r.email === email)?.token ?? null

describe('목 비밀번호 재설정 (011)', () => {
  test('가입한 주소·인증 전·탈퇴·처음 보는 대학 주소 모두 같은 202 { retryAt }, 링크는 가입한 주소만', async () => {
    mockState.pending.push({ email: 'pending@school.ac.kr', token: 'tok-p', lastSentAt: 0 })
    mockState.members.push({ id: 99, displayName: '탈퇴한 회원', email: '', password: '', school: 'school.ac.kr', withdrawn: true })
    const emails = ['organizer@school.ac.kr', 'pending@school.ac.kr', 'gone@school.ac.kr', 'nobody@snu.ac.kr']
    const bodies = await Promise.all(emails.map((e) => requestPasswordReset(e)))
    for (const b of bodies) {
      expect(Object.keys(b)).toEqual(['retryAt'])
      expect(Date.parse(b.retryAt) - Date.now()).toBeGreaterThan(55_000)
    }
    expect(emails.map(tokenOf).map(Boolean)).toEqual([true, false, false, false])
  })

  test('대학 메일이 아니면 400 errors[email], 1분 안 재요청과 24시간 6번째는 가입 여부와 무관하게 429 + retryAt', async () => {
    const bad = await fail(requestPasswordReset('x@gmail.com'))
    expect([bad.status, bad.fieldErrors[0]?.field, bad.fieldErrors[0]?.message]).toEqual([400, 'email', '대학 메일로만 가입할 수 있습니다.'])
    for (const email of ['organizer@school.ac.kr', 'nobody@snu.ac.kr']) {
      await requestPasswordReset(email)
      const e = await fail(requestPasswordReset(email))
      expect([e.status, e.code, typeof e.extra.retryAt]).toEqual([429, 'TOO_MANY_REQUESTS', 'string'])
      const r = mockState.passwordResets.find((x) => x.email === email)!
      r.sentCount = 5
      r.lastSentAt = Date.now() - 120_000
      const daily = await fail(requestPasswordReset(email))
      expect([daily.status, daily.code]).toEqual([429, 'TOO_MANY_REQUESTS'])
    }
    expect(mockState.loginFailures).toBe(0) // 재설정 요청은 로그인 실패에 세지 않는다
  })

  test('확인: 비밀번호 규칙 400(토큰 그대로) → 204(비밀번호 바뀜, 세션 끝, 실패 기록 지움) → 같은 링크 409', async () => {
    mockState.sessionMemberId = 1
    mockState.loginFailures = 5
    await requestPasswordReset('organizer@school.ac.kr')
    const token = tokenOf('organizer@school.ac.kr')!
    const short = await fail(confirmPasswordReset(token, 'short'))
    expect([short.status, short.fieldErrors[0]?.field]).toEqual([400, 'password'])
    await confirmPasswordReset(token, 'new-password-1')
    expect(mockState.members.find((m) => m.id === 1)!.password).toBe('new-password-1')
    expect(mockState.sessionMemberId).toBeNull()
    expect(mockState.loginFailures).toBe(0)
    const used = await fail(confirmPasswordReset(token, 'new-password-2'))
    expect([used.status, used.code]).toEqual([409, 'TOKEN_USED'])
  })

  test('확인 오류: 없는 토큰·새 요청으로 무효·그 사이 탈퇴는 400 INVALID_TOKEN, 1시간 지나면 410', async () => {
    expect((await fail(confirmPasswordReset('nope', 'password1234'))).code).toBe('INVALID_TOKEN')
    await requestPasswordReset('participant@school.ac.kr')
    const first = tokenOf('participant@school.ac.kr')!
    mockState.passwordResets.find((r) => r.email === 'participant@school.ac.kr')!.lastSentAt = 0
    await requestPasswordReset('participant@school.ac.kr')
    expect((await fail(confirmPasswordReset(first, 'password1234'))).code).toBe('INVALID_TOKEN')
    const r = mockState.passwordResets.find((x) => x.email === 'participant@school.ac.kr')!
    r.expiresAt = Date.now() - 1
    const expired = await fail(confirmPasswordReset(r.token!, 'password1234'))
    expect([expired.status, expired.code]).toEqual([410, 'TOKEN_EXPIRED'])
    await requestPasswordReset('outsider@school.ac.kr')
    const t = tokenOf('outsider@school.ac.kr')!
    Object.assign(mockState.members.find((m) => m.id === 3)!, { withdrawn: true })
    expect((await fail(confirmPasswordReset(t, 'password1234'))).code).toBe('INVALID_TOKEN')
  })

  test('탈퇴하면 그 주소의 재설정 요청 기록을 지운다', async () => {
    await requestPasswordReset('outsider@school.ac.kr')
    mockState.sessionMemberId = 3
    mockState.groupBuys = []
    const { withdraw } = await import('../features/auth/api')
    await withdraw('password1234')
    expect(mockState.passwordResets.some((r) => r.email === 'outsider@school.ac.kr')).toBe(false)
  })
})
