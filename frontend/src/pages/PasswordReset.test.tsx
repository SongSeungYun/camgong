import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, onTestFinished, test } from 'vitest'
import { server } from '../mocks/server'
import { mockState } from '../mocks/state'
import { renderApp } from '../test/utils'

const described = (el: HTMLElement) => (el.getAttribute('aria-describedby') ?? '').split(' ').map((id) => document.getElementById(id)?.textContent ?? '').join(' ')

// 011 T011: 로그인 화면 링크와 재설정 요청 화면
describe('비밀번호 재설정 요청 (011)', () => {
  test('로그인 화면에 "비밀번호를 잊었어요" 링크가 /password-reset으로 간다', async () => {
    renderApp('/login')
    expect((await screen.findByRole('link', { name: '비밀번호를 잊었어요' })).getAttribute('href')).toBe('/password-reset')
  })

  test('요청하면 가입 여부와 무관한 안내와 다시 요청할 수 있는 때를 보이고, 그때까지 같은 주소로는 다시 못 누른다', async () => {
    renderApp('/password-reset')
    await screen.findByRole('heading', { name: '비밀번호 재설정', level: 1 })
    await userEvent.type(screen.getByLabelText('이메일'), 'nobody@snu.ac.kr')
    await userEvent.click(screen.getByRole('button', { name: '재설정 링크 받기' }))
    const status = await screen.findByRole('status')
    expect(status.textContent).toContain('가입한 학교 메일이면 재설정 링크를 보냈어요. 1시간 안에 열어 주세요.')
    expect(status.textContent).toMatch(/1분 (미만 )?남음이라 아직 다시 요청할 수 없어요/)
    expect(screen.getByRole('button', { name: '재설정 링크 받기' }).getAttribute('aria-disabled')).toBe('true')
    expect(mockState.passwordResets.find((r) => r.email === 'nobody@snu.ac.kr')?.sentCount).toBe(1)
  })

  test('429면 남은 시간과 함께 다시 시도 안내', async () => {
    server.use(http.post('/api/auth/password-reset', () => HttpResponse.json({ title: 'TOO_MANY_REQUESTS', status: 429, retryAt: new Date(Date.now() + 30 * 60_000).toISOString() }, { status: 429 })))
    renderApp('/password-reset')
    await userEvent.type(await screen.findByLabelText('이메일'), 'organizer@school.ac.kr')
    await userEvent.click(screen.getByRole('button', { name: '재설정 링크 받기' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/잠시 뒤에 다시 시도해 주세요\. \((29|30)분 남음\)/)
  })

  test('대학 메일이 아니면 이메일 칸 옆에 서버 문구', async () => {
    renderApp('/password-reset')
    const email = await screen.findByLabelText('이메일')
    await userEvent.type(email, 'x@gmail.com')
    await userEvent.click(screen.getByRole('button', { name: '재설정 링크 받기' }))
    await waitFor(() => expect(email.getAttribute('aria-invalid')).toBe('true'))
    expect(described(email)).toContain('대학 메일로만 가입할 수 있습니다.')
  })
})

// 011 T012: 새 비밀번호 정하기
const NOTICE = '비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.'
async function resetToken(email = 'organizer@school.ac.kr') {
  const { requestPasswordReset } = await import('../features/auth/api')
  await requestPasswordReset(email)
  return mockState.passwordResets.find((r) => r.email === email)!.token!
}
async function fill(pw: string, again = pw) {
  await userEvent.type(await screen.findByLabelText('새 비밀번호'), pw)
  await userEvent.type(screen.getByLabelText('새 비밀번호 확인'), again)
  await userEvent.click(screen.getByRole('button', { name: '비밀번호 바꾸기' }))
}

describe('새 비밀번호 정하기 (011)', () => {
  test('204면 로그인 화면으로 가서 안내(포커스)하고, 기록(state)에는 남기지 않는다', async () => {
    const token = await resetToken()
    const { router } = renderApp(`/password-reset/confirm?token=${token}`)
    await fill('new-password-1')
    const notice = await screen.findByText(NOTICE)
    expect(router.state.location.pathname).toBe('/login')
    await waitFor(() => expect(document.activeElement).toBe(notice))
    await waitFor(() => expect(router.state.location.state).toBeNull())
    expect(mockState.members.find((m) => m.id === 1)!.password).toBe('new-password-1')
  })

  test('로그인한 채 링크를 열어 바꿔도 화면 세션을 비우고 로그인 화면에 안내한다(자동 로그인 없음)', async () => {
    const token = await resetToken()
    mockState.sessionMemberId = 1
    const { router } = renderApp(`/password-reset/confirm?token=${token}`)
    await screen.findByRole('button', { name: '로그아웃' })
    await fill('new-password-1')
    await screen.findByText(NOTICE)
    expect(router.state.location.pathname).toBe('/login')
    expect(screen.queryByRole('button', { name: '로그아웃' })).toBeNull()
  })

  test('두 칸이 다르면 보내지 않고 확인 칸 옆에 안내', async () => {
    const token = await resetToken()
    let calls = 0
    server.events.on('request:start', ({ request }) => void (request.url.endsWith('/confirm') && calls++))
    onTestFinished(() => server.events.removeAllListeners()) // 실패해도 다음 테스트에 남지 않게
    renderApp(`/password-reset/confirm?token=${token}`)
    await fill('new-password-1', 'new-password-2')
    const again = screen.getByLabelText('새 비밀번호 확인')
    await waitFor(() => expect(described(again)).toContain('두 비밀번호가 달라요.'))
    expect(calls).toBe(0)
  })

  test('비밀번호 규칙 400은 새 비밀번호 칸 옆, 링크는 그대로라 고쳐서 다시 낼 수 있다', async () => {
    const token = await resetToken()
    renderApp(`/password-reset/confirm?token=${token}`)
    await fill('        ')
    const pw = screen.getByLabelText('새 비밀번호')
    await waitFor(() => expect(described(pw)).toContain('공백만으로는 쓸 수 없어요'))
    await userEvent.clear(pw)
    await userEvent.clear(screen.getByLabelText('새 비밀번호 확인'))
    await fill('new-password-1')
    await screen.findByText(NOTICE)
  })

  test.each([
    ['없는 토큰', 'nope', '올바르지 않은 링크예요.'],
    ['쓴 토큰', 'used', '이미 쓴 링크예요.'],
    ['만료된 토큰', 'expired', '링크가 만료됐어요.'],
  ])('%s → 폼을 닫고 이유와 다시 요청 링크', async (_, kind, message) => {
    let token = 'nope'
    if (kind !== 'nope') {
      token = await resetToken()
      const r = mockState.passwordResets.find((x) => x.token === token)!
      if (kind === 'used') r.usedAt = Date.now()
      else r.expiresAt = Date.now() - 1
    }
    renderApp(`/password-reset/confirm?token=${token}`)
    await fill('new-password-1')
    expect((await screen.findByRole('alert')).textContent).toBe(message)
    expect(screen.getByRole('link', { name: '다시 요청하기' }).getAttribute('href')).toBe('/password-reset')
    expect(screen.queryByLabelText('새 비밀번호')).toBeNull()
  })

  test('token이 없으면 바로 올바르지 않은 링크 안내', async () => {
    renderApp('/password-reset/confirm')
    expect((await screen.findByRole('alert')).textContent).toBe('올바르지 않은 링크예요.')
    screen.getByRole('link', { name: '다시 요청하기' })
  })
})
