import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { mockState } from '../../mocks/state'
import { post } from '../../shared/api/client'
import { seedGroupBuy } from '../../test/factories'
import { renderApp } from '../../test/utils'

const EMAIL = 'new@school.ac.kr'

async function fillLogin(email: string, password: string) {
  await userEvent.type(screen.getByLabelText('이메일'), email)
  await userEvent.type(screen.getByLabelText('비밀번호'), password)
  await userEvent.click(screen.getByRole('button', { name: '로그인' }))
}

describe('로그인·보호 라우트', () => {
  test('7. 비로그인으로 보호 라우트 → 로그인, 로그인 뒤에는 항상 /', async () => {
    seedGroupBuy()
    const { router } = renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '로그인' })
    expect(router.state.location.pathname).toBe('/login')
    await fillLogin('organizer@school.ac.kr', 'password1234')
    await screen.findByRole('button', { name: '로그아웃' })
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })

  test('10. 로그인 실패는 어느 쪽이 틀렸는지 알리지 않는다', async () => {
    renderApp('/login')
    await screen.findByRole('heading', { name: '로그인' })
    await fillLogin('organizer@school.ac.kr', 'wrong-password')
    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toBe('이메일 또는 비밀번호가 맞지 않아요.')
  })

  test('10. 연속 실패 잠금은 잠시 뒤 다시 시도하라고 안내', async () => {
    mockState.loginLocked = true
    renderApp('/login')
    await screen.findByRole('heading', { name: '로그인' })
    await fillLogin('organizer@school.ac.kr', 'password1234')
    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('로그인 시도가 너무 많아요')
    expect(alert.textContent).toContain('남음')
  })

  test('18. 인증 전 안내와 재발송 진입이 항상 보이고, 인증 전 로그인도 같은 실패 문구', async () => {
    await post('/api/auth/signup', { email: EMAIL })
    renderApp('/login')
    await screen.findByText(/아직 인증 전이라면 인증 메일 다시 받기/)
    await fillLogin(EMAIL, 'password1234')
    expect((await screen.findByRole('alert')).textContent).toBe('이메일 또는 비밀번호가 맞지 않아요.')
    // 로그인 화면의 재발송 진입: 방금 보냈으니 한도 안내가 나온다
    expect((screen.getByLabelText('인증 메일 받을 이메일') as HTMLInputElement).value).toBe(EMAIL) // 로그인 화면에 입력한 이메일이 따라온다
    await userEvent.click(screen.getByRole('button', { name: '인증 메일 다시 받기' }))
    await screen.findByText(/잠시 뒤에 다시 시도해 주세요/)
  })

  test('19. 비밀번호는 가려지고 어디에도 저장되지 않는다', async () => {
    const { router } = renderApp('/login')
    const pw = await screen.findByLabelText('비밀번호')
    expect(pw.getAttribute('type')).toBe('password')
    await fillLogin('organizer@school.ac.kr', 'password1234')
    await screen.findByRole('button', { name: '로그아웃' })
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
    expect(router.state.location.search + router.state.location.pathname).not.toContain('password')
  })

  test('13. 로그아웃 후 뒤로 가도 이전 내용이 안 보이고 로그인을 요구한다', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 1
    const { router, client } = renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    await userEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    await screen.findByRole('heading', { name: '로그인' })
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    await router.navigate(-1)
    await screen.findByRole('heading', { name: '로그인' })
    expect(screen.queryByText('햇반 24개 공구')).toBeNull()
  })

  test('31. 사용 중 세션이 만료(401)되면 로그인으로 가고 입력 소실을 안내한다', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 1
    const { client } = renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    mockState.sessionExpired = true
    await client.invalidateQueries({ queryKey: ['groupbuy'] })
    await screen.findByRole('heading', { name: '로그인' })
    screen.getByText('로그인이 필요해요. 입력하던 내용은 저장되지 않아요.')
  })
})

describe('가입·인증 (변경 B2: 가입은 이메일만, 이름·비밀번호·동의는 인증 화면)', () => {
  async function signupAs(email = EMAIL) {
    await userEvent.type(screen.getByLabelText('이메일'), email)
    await userEvent.click(screen.getByRole('button', { name: '가입하기' }))
  }
  const newToken = async () => {
    await post('/api/auth/signup', { email: EMAIL })
    return mockState.pending[0]!.token
  }
  async function fillVerify(over: Partial<{ name: string; pw: string; consent: boolean }> = {}) {
    const o = { name: '새내기', pw: 'password1234', consent: true, ...over }
    if (o.name) await userEvent.type(screen.getByLabelText('표시 이름'), o.name)
    if (o.pw) await userEvent.type(screen.getByLabelText('비밀번호'), o.pw)
    if (o.consent) await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: '인증 완료하기' }))
  }

  test('8. 가입 화면은 이메일 하나만 받고, 제출하면 인증 메일 안내와 재발송 대기 안내가 나온다', async () => {
    renderApp('/signup')
    await screen.findByRole('heading', { name: '가입' })
    expect(screen.queryByLabelText('표시 이름')).toBeNull()
    expect(screen.queryByLabelText('비밀번호')).toBeNull()
    expect(screen.queryByRole('checkbox')).toBeNull()
    screen.getByText(/학교 이메일만/)
    await signupAs()
    await screen.findByRole('heading', { name: /인증 메일을 보냈어요/ })
    const resend = screen.getByRole('button', { name: /인증 메일 다시 보내기/ }) as HTMLButtonElement
    expect(resend.disabled).toBe(true)
    screen.getByText(/다시 보낼 수 있어요/)
  })

  test('8. 학교 이메일이 아니면 이메일 옆에 오류', async () => {
    renderApp('/signup')
    await screen.findByRole('heading', { name: '가입' })
    await signupAs('x@gmail.com')
    const input = screen.getByLabelText('이메일')
    await waitFor(() => expect(input.getAttribute('aria-invalid')).toBe('true'))
    expect(document.getElementById(input.getAttribute('aria-describedby')!)!.textContent).toBe('허용된 학교 이메일이 아니에요')
  })

  test('이미 가입된 이메일이면 안내와 로그인 링크', async () => {
    renderApp('/signup')
    await screen.findByRole('heading', { name: '가입' })
    await signupAs('organizer@school.ac.kr')
    await screen.findByText('이미 가입된 이메일이에요. 로그인해 주세요.')
  })

  test('9. 링크를 열기만 하면 요청이 가지 않고 폼이 보인다. 제출하면 로그인 상태로 /', async () => {
    const token = await newToken()
    const { router } = renderApp(`/verify?token=${token}`)
    await screen.findByRole('heading', { name: '이메일 인증' })
    screen.getByLabelText('표시 이름')
    expect(screen.getByLabelText('비밀번호').getAttribute('type')).toBe('password')
    expect(mockState.pending).toHaveLength(1) // 열기만으로는 소진되지 않는다
    expect(mockState.members.some((m) => m.email === EMAIL)).toBe(false)
    await fillVerify()
    await screen.findByRole('button', { name: '로그아웃' })
    expect(router.state.location.pathname).toBe('/')
    expect(mockState.pending).toHaveLength(0)
  })

  test('9. 동의 안내문을 볼 수 있다', async () => {
    renderApp(`/verify?token=${await newToken()}`)
    await userEvent.click(await screen.findByText('안내문 보기'))
    screen.getByText(/수집하는 항목/)
  })

  test('9. 필드 오류는 각 입력란 옆에 한 번에 보이고 입력값이 유지되며, 같은 링크로 고쳐 다시 제출하면 성공한다', async () => {
    const token = await newToken()
    renderApp(`/verify?token=${token}`)
    await screen.findByRole('heading', { name: '이메일 인증' })
    await fillVerify({ name: '가', pw: 'short' })
    const name = screen.getByLabelText('표시 이름') as HTMLInputElement
    const pw = screen.getByLabelText('비밀번호') as HTMLInputElement
    await waitFor(() => expect(name.getAttribute('aria-invalid')).toBe('true'))
    expect(pw.getAttribute('aria-invalid')).toBe('true')
    expect(document.getElementById(pw.getAttribute('aria-describedby')!)!.textContent).toBe('8자 이상 입력해 주세요')
    expect(name.value).toBe('가')
    expect(pw.value).toBe('short')
    expect(mockState.pending).toHaveLength(1) // 400에서는 토큰이 소진되지 않는다
    await userEvent.clear(name)
    await userEvent.type(name, '새내기')
    await userEvent.clear(pw)
    await userEvent.type(pw, 'password1234')
    await userEvent.click(screen.getByRole('button', { name: '인증 완료하기' }))
    await screen.findByRole('button', { name: '로그아웃' })
  })

  test('9. 동의하지 않고 제출하면 동의 오류', async () => {
    renderApp(`/verify?token=${await newToken()}`)
    await screen.findByRole('heading', { name: '이메일 인증' })
    await fillVerify({ consent: false })
    await screen.findByText('동의가 필요해요')
    expect(screen.queryByRole('button', { name: '로그아웃' })).toBeNull()
  })

  test('9. 이름이 이미 쓰이면 이름 입력란 오류이고, 이름만 바꿔 다시 제출하면 성공한다', async () => {
    renderApp(`/verify?token=${await newToken()}`)
    await screen.findByRole('heading', { name: '이메일 인증' })
    await fillVerify({ name: '개설자' })
    const name = screen.getByLabelText('표시 이름') as HTMLInputElement
    await waitFor(() => expect(name.getAttribute('aria-invalid')).toBe('true'))
    expect(document.getElementById(name.getAttribute('aria-describedby')!)!.textContent).toBe('이미 쓰이는 이름이에요. 다른 이름을 입력해 주세요.')
    expect((screen.getByLabelText('비밀번호') as HTMLInputElement).value).toBe('password1234')
    await userEvent.clear(name)
    await userEvent.type(name, '다른이름')
    await userEvent.click(screen.getByRole('button', { name: '인증 완료하기' }))
    await screen.findByRole('button', { name: '로그아웃' })
  })

  test.each([
    ['expired-token', '링크가 만료됐어요.', true],
    ['used-token', '이미 쓴 링크예요.', false],
    ['garbage', '올바르지 않은 링크예요.', false],
  ])('9. 인증 링크 %s → 이유 + 가입 화면 링크 (만료는 재발송 안내)', async (token, message, canResend) => {
    renderApp(`/verify?token=${token}`)
    await screen.findByLabelText('표시 이름')
    await fillVerify()
    await screen.findByText(message)
    expect(screen.getByRole('link', { name: '가입 화면으로' }).getAttribute('href')).toBe('/signup')
    expect(!!screen.queryByRole('button', { name: '인증 메일 다시 받기' })).toBe(canResend)
  })

  test('9. token 쿼리가 없으면 올바르지 않은 링크와 같은 안내', async () => {
    renderApp('/verify')
    await screen.findByText('올바르지 않은 링크예요.')
    screen.getByRole('link', { name: '가입 화면으로' })
  })

  test('19. 인증 화면의 비밀번호도 주소·저장소에 남지 않는다', async () => {
    const { router } = renderApp(`/verify?token=${await newToken()}`)
    await screen.findByRole('heading', { name: '이메일 인증' })
    await fillVerify({ pw: 'secret-pass-99' })
    await screen.findByRole('button', { name: '로그아웃' })
    expect(router.state.location.search + router.state.location.pathname).not.toContain('secret-pass-99')
    expect(JSON.stringify({ ...localStorage })).not.toContain('secret-pass-99')
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('secret-pass-99')
  })
})
