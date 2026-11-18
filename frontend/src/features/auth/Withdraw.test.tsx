import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { server } from '../../mocks/server'
import { mockState } from '../../mocks/state'
import { makeGroupBuy } from '../../test/factories'
import { renderApp } from '../../test/utils'

// 010 T012: 내 활동의 탈퇴 영역과 대화상자
const asOutsider = (groupBuys = [] as ReturnType<typeof makeGroupBuy>[]) => {
  mockState.sessionMemberId = 3
  mockState.groupBuys = groupBuys
}
async function openDialog() {
  const region = await screen.findByRole('region', { name: '탈퇴' })
  await userEvent.click(within(region).getByRole('button', { name: '탈퇴' }))
  return screen.getByRole('dialog', { name: '탈퇴할까요?' })
}
const pw = () => within(screen.getByRole('dialog')).getByLabelText('비밀번호') as HTMLInputElement

describe('탈퇴 (010)', () => {
  test('대화상자: 열면 비밀번호 입력에 포커스, 되돌릴 수 없다는 안내, Esc로 닫으면 탈퇴 버튼으로 돌아오고 요청 없음', async () => {
    asOutsider()
    let calls = 0
    server.events.on('request:start', ({ request }) => void (request.url.endsWith('/withdraw') && calls++))
    renderApp('/me')
    const dialog = await openDialog()
    within(dialog).getByText(/되돌릴 수 없어요/)
    expect(document.activeElement).toBe(pw())
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(within(screen.getByRole('region', { name: '탈퇴' })).getByRole('button', { name: '탈퇴' }))
    expect(calls).toBe(0)
    server.events.removeAllListeners()
  })

  test('비밀번호가 틀리면 대화상자 안에 필드 오류, 입력을 비우고 다시 비밀번호에 포커스', async () => {
    asOutsider()
    renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'wrong-password')
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '탈퇴하기' }))
    await within(screen.getByRole('dialog')).findByText('비밀번호가 맞지 않습니다.')
    expect(pw().getAttribute('aria-invalid')).toBe('true')
    expect(pw().value).toBe('')
    expect(document.activeElement).toBe(pw())
    expect(mockState.members.find((m) => m.id === 3)!.withdrawn).toBeFalsy()
  })

  test('잠금(429)이면 대화상자 안에 잠금 안내', async () => {
    asOutsider()
    mockState.loginLocked = true
    renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    await userEvent.keyboard('{Enter}') // 입력칸에서 Enter로 제출
    await within(screen.getByRole('dialog')).findByText(/로그인 시도가 너무 많아요/)
  })

  test('409 진행 중 공구: 대화상자를 닫고 막는 공구를 링크와 안내로 보여준다(PENDING_CLOSE가 역할보다 먼저)', async () => {
    const past = new Date(Date.now() - 60_000).toISOString()
    asOutsider([
      makeGroupBuy({ id: 3, title: '내가 연 공구', organizerId: 3, participations: [{ memberId: 3, quantity: 1 }] }),
      makeGroupBuy({ id: 5, title: '참여한 공구', organizerId: 1, participations: [{ memberId: 1, quantity: 1 }, { memberId: 3, quantity: 1 }] }),
      makeGroupBuy({ id: 7, title: '마감 지난 내 공구', organizerId: 3, participations: [{ memberId: 3, quantity: 1 }], deadline: past }),
    ])
    renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '탈퇴하기' }))
    const alert = await screen.findByRole('alert', { name: '진행 중인 공구가 있어 탈퇴할 수 없어요.' })
    expect(screen.queryByRole('dialog')).toBeNull()
    const rows = within(alert).getAllByRole('listitem')
    expect(rows.map((r) => r.textContent)).toEqual([
      '내가 연 공구 — 공구를 취소하세요',
      '참여한 공구 — 참여를 취소하세요',
      '마감 지난 내 공구 — 확정된 뒤 다시 시도하세요',
    ])
    expect(within(rows[0]!).getByRole('link', { name: '내가 연 공구' }).getAttribute('href')).toBe('/groupbuys/3')
    expect(mockState.members.find((m) => m.id === 3)!.withdrawn).toBeFalsy()
  })

  test('서버 오류면 대화상자 안에 일반 오류(입력 유지)', async () => {
    asOutsider()
    server.use(http.post('/api/auth/withdraw', () => new HttpResponse(null, { status: 500 })))
    renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '탈퇴하기' }))
    await within(screen.getByRole('dialog')).findByText('잠시 문제가 생겼어요. 다시 시도해 주세요.')
    expect(pw().value).toBe('password1234')
  })

  test('성공하면 세션을 비우고 목록으로 가서 "탈퇴했어요"', async () => {
    asOutsider()
    const { router } = renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '탈퇴하기' }))
    await screen.findByText('탈퇴했어요.')
    expect(router.state.location.pathname).toBe('/')
    screen.getByRole('link', { name: '로그인' })
    await waitFor(() => expect(mockState.members.find((m) => m.id === 3)!.withdrawn).toBe(true))
  })
})
