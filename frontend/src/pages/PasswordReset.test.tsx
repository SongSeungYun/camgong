import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
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
