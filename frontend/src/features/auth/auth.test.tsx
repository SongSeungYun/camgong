import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { mockState } from '../../mocks/state'
import { post } from '../../shared/api/client'
import { seedGroupBuy } from '../../test/factories'
import { renderApp } from '../../test/utils'

const signupBody = { email: 'new@school.ac.kr', displayName: '새내기', password: 'password1234', consent: true, consentVersion: 'v1' }

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
    await post('/api/auth/signup', signupBody)
    renderApp('/login')
    await screen.findByText(/아직 인증 전이라면 인증 메일 다시 받기/)
    await fillLogin(signupBody.email, signupBody.password)
    expect((await screen.findByRole('alert')).textContent).toBe('이메일 또는 비밀번호가 맞지 않아요.')
    // 로그인 화면의 재발송 진입: 방금 보냈으니 한도 안내가 나온다
    await userEvent.type(screen.getByLabelText('인증 메일 받을 이메일'), signupBody.email)
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

describe('가입·인증', () => {
  async function fillSignup(over: Partial<typeof signupBody> = {}) {
    const b = { ...signupBody, ...over }
    await userEvent.type(screen.getByLabelText('이메일'), b.email)
    await userEvent.type(screen.getByLabelText('표시 이름'), b.displayName)
    await userEvent.type(screen.getByLabelText('비밀번호'), b.password)
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: '가입하기' }))
  }

  test('8. 가입하면 인증 메일 안내와 재발송 대기 안내가 나온다', async () => {
    renderApp('/signup')
    await screen.findByRole('heading', { name: '가입' })
    await fillSignup()
    await screen.findByRole('heading', { name: /인증 메일을 보냈어요/ })
    const resend = screen.getByRole('button', { name: /인증 메일 다시 보내기/ }) as HTMLButtonElement
    expect(resend.disabled).toBe(true)
    screen.getByText(/다시 보낼 수 있어요/)
  })

  test('16. 서버 필드 오류는 해당 필드 옆에 보인다', async () => {
    renderApp('/signup')
    await screen.findByRole('heading', { name: '가입' })
    await fillSignup({ password: 'short' })
    const pw = screen.getByLabelText('비밀번호')
    await waitFor(() => expect(pw.getAttribute('aria-invalid')).toBe('true'))
    expect(document.getElementById(pw.getAttribute('aria-describedby')!)!.textContent).toBe('8자 이상 입력해 주세요')
    expect(screen.getByLabelText('이메일').getAttribute('aria-invalid')).toBe('false')
  })

  test('이미 가입된 이메일이면 안내와 로그인 링크', async () => {
    renderApp('/signup')
    await screen.findByRole('heading', { name: '가입' })
    await fillSignup({ email: 'organizer@school.ac.kr' })
    await screen.findByText('이미 가입된 이메일이에요. 로그인해 주세요.')
  })

  test('9. 유효한 인증 링크: 확인 버튼을 눌러야 소진되고, 성공하면 로그인 상태로 /', async () => {
    await post('/api/auth/signup', signupBody)
    const token = mockState.pending[0]!.token
    const { router } = renderApp(`/verify?token=${token}`)
    await screen.findByRole('heading', { name: '이메일 인증' })
    expect(mockState.pending).toHaveLength(1) // 링크를 여는 것만으로는 소진되지 않는다
    await userEvent.click(screen.getByRole('button', { name: '인증 완료하기' }))
    await screen.findByRole('button', { name: '로그아웃' })
    expect(router.state.location.pathname).toBe('/')
  })

  test.each([
    ['expired-token', '링크가 만료됐어요.'],
    ['used-token', '이미 쓴 링크예요.'],
    ['garbage', '올바르지 않은 링크예요.'],
  ])('9. 인증 링크 %s → 이유 + 재발송', async (token, message) => {
    renderApp(`/verify?token=${token}`)
    await userEvent.click(await screen.findByRole('button', { name: '인증 완료하기' }))
    await screen.findByText(message)
    screen.getByRole('button', { name: '인증 메일 다시 받기' })
  })

  test('표시 이름 충돌이면 이름 입력란이 나오고 새 이름으로 완료된다', async () => {
    await post('/api/auth/signup', { ...signupBody, displayName: '개설자' })
    renderApp(`/verify?token=${mockState.pending[0]!.token}`)
    await userEvent.click(await screen.findByRole('button', { name: '인증 완료하기' }))
    await screen.findByText('이미 쓰이는 이름이에요. 다른 이름을 입력해 주세요.')
    await userEvent.type(screen.getByLabelText('새 표시 이름'), '다른이름')
    await userEvent.click(screen.getByRole('button', { name: '이 이름으로 인증 완료하기' }))
    await screen.findByRole('button', { name: '로그아웃' })
  })
})
