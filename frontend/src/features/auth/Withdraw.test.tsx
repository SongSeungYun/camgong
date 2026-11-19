import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { onlineManager } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, test } from 'vitest'
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
  afterEach(() => server.events.removeAllListeners())

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
    await screen.findByRole('link', { name: '로그인' }) // 세션이 비워졌다
    await new Promise((r) => setTimeout(r, 100)) // 세션을 비운 뒤에도 보호 라우트가 /login으로 보내지 않는다
    expect(router.state.location.pathname).toBe('/')
    screen.getByText('탈퇴했어요.')
    await waitFor(() => expect(mockState.members.find((m) => m.id === 3)!.withdrawn).toBe(true))
  })

  test('리뷰 1: 거부된 뒤 비밀번호가 뮤테이션 캐시에 남지 않는다', async () => {
    asOutsider()
    const { client } = renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'wrong-password')
    await userEvent.keyboard('{Enter}')
    await within(screen.getByRole('dialog')).findByText('비밀번호가 맞지 않습니다.')
    await waitFor(() => expect(client.getMutationCache().getAll().map((m) => m.state.variables)).not.toContain('wrong-password'))
  })

  test('리뷰 2: 같은 틱에 두 번 제출해도 요청은 한 번', async () => {
    asOutsider()
    let calls = 0
    server.events.on('request:start', ({ request }) => void (request.url.endsWith('/withdraw') && calls++))
    renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    const form = pw().closest('form')!
    fireEvent.submit(form)
    fireEvent.submit(form)
    await screen.findByText('탈퇴했어요.')
    expect(calls).toBe(1)
  })

  test('리뷰 3: 성공 뒤 로그인 화면에 세션 만료 안내가 뜨지 않는다', async () => {
    asOutsider()
    const { router } = renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    await userEvent.keyboard('{Enter}')
    await screen.findByText('탈퇴했어요.')
    await router.navigate('/login')
    await screen.findByRole('heading', { name: '로그인' })
    expect(screen.queryByText('로그인이 필요해요. 입력하던 내용은 저장되지 않아요.')).toBeNull()
  })

  test('리뷰 8: 세션이 이미 끝났으면(401) 로그인으로 간다', async () => {
    asOutsider()
    const { router } = renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    mockState.sessionMemberId = null
    await userEvent.keyboard('{Enter}')
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })

  test('리뷰 8: 처리 중 Esc로 닫히지 않는다, 409로 닫힌 뒤 포커스는 탈퇴 버튼·비밀번호는 비워진다', async () => {
    asOutsider([makeGroupBuy({ organizerId: 3, participations: [{ memberId: 3, quantity: 1 }] })])
    server.use(http.post('/api/auth/withdraw', async () => {
      await new Promise((r) => setTimeout(r, 150))
      return HttpResponse.json({ title: 'ACTIVE_GROUPBUY', status: 409, blockers: [{ groupBuyId: 1, title: '햇반 24개 공구', role: 'ORGANIZER', status: 'OPEN' }] }, { status: 409 })
    }))
    renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    await userEvent.keyboard('{Enter}')
    await userEvent.keyboard('{Escape}')
    screen.getByRole('dialog')
    await screen.findByRole('alert', { name: '진행 중인 공구가 있어 탈퇴할 수 없어요.' })
    const opener = within(screen.getByRole('region', { name: '탈퇴' })).getByRole('button', { name: '탈퇴' })
    await waitFor(() => expect(document.activeElement).toBe(opener))
    await userEvent.click(opener)
    expect(pw().value).toBe('')
  })

  test('리뷰 10: 필드 오류는 다시 입력하기 시작하면 걷힌다', async () => {
    asOutsider()
    renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'wrong-password')
    await userEvent.keyboard('{Enter}')
    await within(screen.getByRole('dialog')).findByText('비밀번호가 맞지 않습니다.')
    await userEvent.type(pw(), 'p')
    expect(pw().getAttribute('aria-invalid')).toBe('false')
  })

  test('검수 2: 오프라인이어도 대화상자에 갇히지 않는다(요청을 바로 보내고 실패면 안내)', async () => {
    asOutsider()
    renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    onlineManager.setOnline(false)
    try {
      server.use(http.post('/api/auth/withdraw', () => HttpResponse.error()))
      await userEvent.keyboard('{Enter}')
      await within(screen.getByRole('dialog')).findByText('잠시 문제가 생겼어요. 다시 시도해 주세요.')
      await userEvent.keyboard('{Escape}')
      expect(screen.queryByRole('dialog')).toBeNull()
    } finally {
      onlineManager.setOnline(true)
    }
  })

  test('검수 4: 대화상자의 "되돌릴 수 없어요" 안내가 aria-describedby로 연결된다', async () => {
    asOutsider()
    renderApp('/me')
    const dialog = await openDialog()
    const desc = document.getElementById(dialog.getAttribute('aria-describedby') ?? '')
    expect(desc?.textContent).toMatch(/되돌릴 수 없어요/)
  })

  test('검수 5: 버튼에 포커스가 가지 않는 브라우저(Safari)에서도 409 뒤 탈퇴 버튼으로 돌아온다', async () => {
    asOutsider([makeGroupBuy({ organizerId: 3, participations: [{ memberId: 3, quantity: 1 }] })])
    renderApp('/me')
    const region = await screen.findByRole('region', { name: '탈퇴' })
    const opener = within(region).getByRole('button', { name: '탈퇴' })
    fireEvent.click(opener) // 클릭해도 포커스가 버튼으로 가지 않는 경우
    await userEvent.type(pw(), 'password1234')
    await userEvent.keyboard('{Enter}')
    await screen.findByRole('alert', { name: '진행 중인 공구가 있어 탈퇴할 수 없어요.' })
    await waitFor(() => expect(document.activeElement).toBe(opener))
  })

  test('검수 6: "탈퇴했어요" 안내로 포커스가 가고, 주소 기록(state)에는 남지 않는다', async () => {
    asOutsider()
    const { router } = renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    await userEvent.keyboard('{Enter}')
    const notice = await screen.findByText('탈퇴했어요.')
    await waitFor(() => expect(document.activeElement).toBe(notice))
    await waitFor(() => expect(router.state.location.state).toBeNull())
    screen.getByText('탈퇴했어요.') // 화면에서는 남아 있다
  })

  test('T018: 탈퇴 영역과 대화상자 안 모두 지우는 것·남는 것을 동의문과 같게 말하고, 대화상자 설명(aria-describedby)에도 들어간다', async () => {
    asOutsider()
    renderApp('/me')
    const region = await screen.findByRole('region', { name: '탈퇴' })
    const erased = '이메일·표시 이름·비밀번호, 내 알림, 내가 연 공구의 설명'
    const kept = '함께한 공구의 수량과 입금·수령 기록, 내가 연 공구의 제목·상품명·상품 링크·오픈채팅 링크, 소속 학교, 가입·탈퇴 시각, 동의 시각·버전'
    const how = '모집 중인 공구는 참여를 취소하거나 내가 연 공구를 취소하면 되고, 마감 확정을 기다리는 공구는 확정된 뒤에 할 수 있어요'
    expect(region.textContent).toContain(how) // T030
    expect(region.textContent).toContain(erased)
    expect(region.textContent).toContain(kept)
    const dialog = await openDialog()
    expect(dialog.textContent).toContain(erased)
    expect(dialog.textContent).toContain(kept)
    const desc = document.getElementById(dialog.getAttribute('aria-describedby') ?? '')!.textContent
    expect(desc).toMatch(/되돌릴 수 없어요/)
    expect(desc).toContain(erased)
    expect(desc).toContain(kept)
  })

  test('T018: 목 탈퇴는 그 회원이 연 공구(상태 무관)의 설명만 비운다', async () => {
    asOutsider([
      makeGroupBuy({ id: 3, title: '완료된 내 공구', organizerId: 3, status: 'COMPLETED', description: '010-1234-5678로 입금', participations: [{ memberId: 3, quantity: 2 }, { memberId: 1, quantity: 2 }] }),
      makeGroupBuy({ id: 4, title: '실패한 내 공구', organizerId: 3, status: 'FAILED', description: '계좌 110-123', participations: [{ memberId: 3, quantity: 1 }] }),
      makeGroupBuy({ id: 5, title: '남의 공구', organizerId: 1, status: 'COMPLETED', description: '남의 설명', participations: [{ memberId: 1, quantity: 2 }, { memberId: 3, quantity: 2 }] }),
    ])
    renderApp('/me')
    await openDialog()
    await userEvent.type(pw(), 'password1234')
    await userEvent.keyboard('{Enter}')
    await screen.findByText('탈퇴했어요.')
    expect(mockState.groupBuys.find((g) => g.id === 3)!.description).toBeNull()
    expect(mockState.groupBuys.find((g) => g.id === 4)!.description).toBeNull()
    expect(mockState.groupBuys.find((g) => g.id === 5)!.description).toBe('남의 설명')
  })

  test('T018: 탈퇴한 개설자의 완료 공구(설명 null)를 참여자가 열면 설명 없이 나머지가 그려진다', async () => {
    mockState.sessionMemberId = 1
    mockState.groupBuys = [makeGroupBuy({ id: 3, title: '완료된 공구', organizerId: 3, status: 'COMPLETED', description: null, participations: [{ memberId: 3, quantity: 2 }, { memberId: 1, quantity: 2 }] })]
    mockState.members.find((m) => m.id === 3)!.withdrawn = true
    renderApp('/groupbuys/3')
    await screen.findByRole('heading', { name: '완료된 공구', level: 1 })
    screen.getByText('햇반 210g')
    expect(document.querySelector('article [class*=desc]')).toBeNull() // 빈 설명 문단도 그리지 않는다
  })
})
