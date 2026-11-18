import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { server } from '../../../mocks/server'
import { mockState } from '../../../mocks/state'
import { renderApp } from '../../../test/utils'

// 009 T014. 시드: school.ac.kr 공구 대부분, 47·48 snu.ac.kr(모집 중은 48 하나), 49·50 snue.ac.kr
const combo = () => screen.getByRole('combobox', { name: '학교 검색' })
// 비로그인 카드의 학교 표시(로그인하면 늘 내 학교라 카드에 넣지 않는다, 검수 8)
const cardSchools = () => screen.getAllByRole('listitem').map((li) => within(li).getByText(/^학교 · /).textContent!.replace('학교 · ', ''))
const listbox = () => document.getElementById(combo().getAttribute('aria-controls')!)!

describe('학교 범위 목록 (009)', () => {
  test('비로그인 목록 카드에 공구의 학교가 보인다(전국)', async () => {
    renderApp('/')
    await screen.findAllByRole('listitem')
    expect(new Set(cardSchools()).size).toBeGreaterThan(1)
  })

  test('학교 검색: 입력하면 후보가 뜨고 키보드로 골라 목록을 거르며 주소에 남는다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snu')
    const box = await screen.findByRole('listbox', { name: '학교 후보' })
    expect(within(box).getAllByRole('option').map((o) => o.textContent)).toEqual(['snu.ac.kr', 'snue.ac.kr'])
    expect(combo().getAttribute('aria-expanded')).toBe('true')
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{ArrowUp}')
    expect(combo().getAttribute('aria-activedescendant')).toBe(within(box).getAllByRole('option')[0]!.id)
    expect(within(box).getAllByRole('option')[0]!.getAttribute('aria-selected')).toBe('true')
    await userEvent.keyboard('{Enter}')
    await waitFor(() => expect(router.state.location.search).toContain('school=snu.ac.kr'))
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(document.activeElement).toBe(combo())
    screen.getByText('선택한 학교 · snu.ac.kr')
    await waitFor(() => expect(cardSchools()).toEqual(['snu.ac.kr']))
  })

  test('마우스로 후보를 눌러도 고를 수 있고, 학교 선택 해제로 전국으로 돌아간다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snue')
    await userEvent.click(await screen.findByRole('option', { name: 'snue.ac.kr' }))
    await waitFor(() => expect(router.state.location.search).toContain('school=snue.ac.kr'))
    await userEvent.click(screen.getByRole('button', { name: '학교 선택 해제' }))
    await waitFor(() => expect(router.state.location.search).not.toContain('school='))
    expect(screen.queryByText(/선택한 학교/)).toBeNull()
  })

  test('Esc는 후보를 닫고 포커스는 입력칸에 남는다, 고르기 전에는 목록을 거르지 않는다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snu')
    await screen.findByRole('listbox')
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(listbox().hidden).toBe(true) // 접혀도 aria-controls 대상은 남는다(검수 7)
    expect(combo().getAttribute('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(combo())
    expect(router.state.location.search).not.toContain('school=')
  })

  test('맞는 학교가 없으면 안내한다', async () => {
    renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'zzz')
    await screen.findByText('맞는 학교가 없어요.')
  })

  test('학교 후보를 불러오지 못하면 안내한다', async () => {
    server.use(http.get('/api/schools', () => new HttpResponse(null, { status: 500 })))
    renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snu')
    await screen.findByText('학교 후보를 불러오지 못했어요. 다시 입력해 주세요.')
  })

  test('주소의 school은 새로고침해도 유지되고 그 학교만 보인다', async () => {
    let sent: string | null = null
    server.use(http.get('/api/groupbuys', ({ request }) => void (sent = new URL(request.url).searchParams.get('school'))))
    renderApp('/?school=snu.ac.kr')
    await screen.findByText('선택한 학교 · snu.ac.kr')
    await waitFor(() => expect(sent).toBe('snu.ac.kr'))
    await waitFor(() => expect(cardSchools()).toEqual(['snu.ac.kr']))
  })

  test('학교를 고른 채 결과가 없으면 "검색 결과 없음" 문구', async () => {
    renderApp('/?school=snu.ac.kr&category=STATIONERY')
    await screen.findByText('검색 결과가 없어요. 다른 검색어나 조건을 써보세요.')
  })

  test('로그인하면 학교 검색이 없고 "우리 학교" 머리말, 주소의 school은 보내지 않는다', async () => {
    mockState.sessionMemberId = 1
    let sent: string | null = 'unset'
    server.use(http.get('/api/groupbuys', ({ request }) => void (sent = new URL(request.url).searchParams.get('school'))))
    renderApp('/?school=snu.ac.kr')
    await screen.findByText('우리 학교 · school.ac.kr')
    expect(screen.queryByRole('combobox', { name: '학교 검색' })).toBeNull()
    expect(screen.queryByText(/선택한 학교/)).toBeNull()
    await waitFor(() => expect(sent).toBeNull())
  })

  test('비로그인으로 본 전국 목록 캐시가 로그인 뒤 목록에 섞이지 않는다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    expect(cardSchools()).toContain('snue.ac.kr')
    await router.navigate('/login')
    await userEvent.type(await screen.findByLabelText('이메일'), 'organizer@school.ac.kr')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))
    await screen.findByText('우리 학교 · school.ac.kr')
    // 전국 캐시가 잠깐이라도 보이면 안 된다: 머리말이 뜬 순간 다른 학교 공구(48·49·50) 카드가 없다
    const otherSchool = () => ['48', '49', '50'].some((id) => document.querySelector(`a[href="/groupbuys/${id}"]`))
    expect(otherSchool()).toBe(false)
    await screen.findAllByRole('listitem')
    expect(otherSchool()).toBe(false)
    expect(screen.queryByText(/^학교 · /)).toBeNull() // 로그인 카드에는 학교 표시가 없다
  })

  test('로그인 확인이 실패(5xx)한 상태에서는 학교 검색을 보이지 않고 school도 보내지 않는다', async () => {
    let sent: string | null = 'unset'
    server.use(
      http.get('/api/auth/me', () => new HttpResponse(null, { status: 503 })),
      http.get('/api/groupbuys', ({ request }) => void (sent = new URL(request.url).searchParams.get('school'))),
    )
    renderApp('/?school=snu.ac.kr')
    await screen.findAllByRole('listitem')
    expect(screen.queryByRole('combobox', { name: '학교 검색' })).toBeNull()
    expect(sent).toBeNull()
  })

  test('주소의 school은 소문자 학교 키로 읽는다', async () => {
    renderApp('/?school=SNU.ac.kr')
    await screen.findByText('선택한 학교 · snu.ac.kr')
    await waitFor(() => expect(cardSchools()).toEqual(['snu.ac.kr']))
  })

  test('후보를 고르지 않고 Enter면 아무것도 거르지 않는다, ↑는 마지막 후보로·↓는 끝에서 처음으로 돈다, 입력칸을 벗어나면 닫힌다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snu')
    const box = await screen.findByRole('listbox')
    await userEvent.keyboard('{Enter}')
    expect(router.state.location.search).not.toContain('school=')
    const opts = within(box).getAllByRole('option')
    await userEvent.keyboard('{ArrowUp}')
    expect(combo().getAttribute('aria-activedescendant')).toBe(opts[1]!.id)
    await userEvent.keyboard('{ArrowDown}')
    expect(combo().getAttribute('aria-activedescendant')).toBe(opts[0]!.id)
    await userEvent.tab()
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  test('고른 학교와 후보 수·결과 없음은 늘 붙어 있는 상태 영역으로 알린다', async () => {
    renderApp('/')
    await screen.findAllByRole('listitem')
    const live = screen.getByTestId('school-status')
    expect(live.getAttribute('role')).toBe('status')
    await userEvent.type(combo(), 'snu')
    await waitFor(() => expect(live.textContent).toBe('학교 후보 2개'))
    await userEvent.keyboard('{ArrowDown}{Enter}')
    await waitFor(() => expect(live.textContent).toBe('snu.ac.kr 공구만 보여요.'))
    expect(screen.getByTestId('school-status')).toBe(live) // 같은 노드가 남아 있다
    await userEvent.click(screen.getByRole('button', { name: '학교 선택 해제' }))
    await waitFor(() => expect(live.textContent).toBe('전국 공구를 보여요.'))
  })

  test('검수 1: 후보가 줄어 하이라이트가 범위를 벗어나면 초기화되고, Enter는 고른 학교를 풀지 않는다', async () => {
    const { router } = renderApp('/?school=snu.ac.kr')
    await screen.findByText('선택한 학교 · snu.ac.kr')
    await userEvent.type(combo(), 'snu')
    await waitFor(() => expect(within(listbox()).getAllByRole('option')).toHaveLength(2))
    await userEvent.keyboard('{ArrowDown}{ArrowDown}') // 두 번째(snue)
    await userEvent.type(combo(), '.')
    await waitFor(() => expect(within(listbox()).getAllByRole('option').map((o) => o.textContent)).toEqual(['snu.ac.kr']))
    expect(combo().getAttribute('aria-activedescendant')).toBeNull()
    await userEvent.keyboard('{Enter}')
    expect(router.state.location.search).toContain('school=snu.ac.kr')
    screen.getByText('선택한 학교 · snu.ac.kr')
  })

  test('검수 2: 이전 검색어의 후보는 새 검색어 아래에 보이지도, 골라지지도 않는다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snu')
    await waitFor(() => expect(within(listbox()).getAllByRole('option')).toHaveLength(2))
    await userEvent.clear(combo())
    await userEvent.type(combo(), 'zzz')
    expect(within(listbox()).queryAllByRole('option', { hidden: true })).toHaveLength(0)
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{Enter}')
    expect(router.state.location.search).not.toContain('school=')
    await screen.findByText('맞는 학교가 없어요.')
  })

  test('검수 5: 입력칸에 다시 포커스하면 후보가 다시 열린다', async () => {
    renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snu')
    await waitFor(() => expect(listbox().hidden).toBe(false))
    await userEvent.tab()
    expect(listbox().hidden).toBe(true)
    await userEvent.click(combo())
    expect(listbox().hidden).toBe(false)
    expect(combo().getAttribute('aria-expanded')).toBe('true')
  })

  test('검수 6: 다시 검색하는 동안에도 고른 학교가 화면에 남는다(상태 영역이 아닌 글)', async () => {
    renderApp('/?school=snu.ac.kr')
    const sel = await screen.findByText('선택한 학교 · snu.ac.kr')
    expect(sel.closest('[role=status]')).toBeNull()
    await userEvent.type(combo(), 'sn')
    await waitFor(() => expect(within(listbox()).getAllByRole('option').length).toBeGreaterThan(0))
    screen.getByText('선택한 학교 · snu.ac.kr')
  })

  test('검수 4: 비로그인으로 학교를 골라 뒀는데 다른 탭에서 로그인돼 응답 범위가 다르면 캐시를 버리고 로그인을 다시 확인한다', async () => {
    renderApp('/?school=snu.ac.kr')
    await screen.findByText('선택한 학교 · snu.ac.kr')
    await waitFor(() => expect(cardSchools()).toEqual(['snu.ac.kr']))
    mockState.sessionMemberId = 1 // 다른 탭에서 로그인
    await userEvent.click(screen.getByRole('button', { name: '생활용품' }))
    await screen.findByText('우리 학교 · school.ac.kr')
    expect(screen.queryByRole('combobox', { name: '학교 검색' })).toBeNull()
  })

  test('검수 4: 로그인 상태인데 서버 세션이 끝나 전국 목록이 오면 캐시를 버리고 로그인을 다시 확인한다', async () => {
    mockState.sessionMemberId = 1
    renderApp('/')
    await screen.findByText('우리 학교 · school.ac.kr')
    await screen.findAllByRole('listitem')
    mockState.sessionMemberId = null // 다른 탭에서 로그아웃
    await userEvent.click(screen.getByRole('button', { name: '주방' })) // 모집 중 주방에 snu 공구(48)가 있다
    await screen.findByRole('combobox', { name: '학교 검색' })
    expect(screen.queryByText('우리 학교 · school.ac.kr')).toBeNull()
  })

  test('재검수 1: 범위 어긋남이 계속되면 "불러오는 중"에 멈추지 않고 다시 시도를 보여준다', async () => {
    mockState.sessionMemberId = 1
    // 서버가 계속 다른 학교 카드를 준다(로그인 학교는 school.ac.kr)
    server.use(http.get('/api/groupbuys', () => HttpResponse.json({ items: [{ id: 48, title: 't', itemName: 'i', category: 'FOOD', unitPrice: 1, securedQuantity: 1, totalQuantity: 2, deadline: '2099-01-01T00:00:00Z', status: 'OPEN', school: 'snu.ac.kr' }], nextCursor: null })))
    renderApp('/')
    await screen.findByRole('button', { name: '다시 시도' }, { timeout: 3000 })
    expect(screen.queryByRole('link', { name: /^t/ })).toBeNull() // 어긋난 카드는 보이지 않는다
  })

  test('재검수 2: 고른 학교가 풀리면(주소가 바뀌면) 이전 선택 알림을 남기지 않는다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    const live = screen.getByTestId('school-status')
    await userEvent.type(combo(), 'snue')
    await waitFor(() => expect(within(listbox()).getAllByRole('option')).toHaveLength(1))
    await userEvent.keyboard('{ArrowDown}{Enter}')
    await waitFor(() => expect(live.textContent).toBe('snue.ac.kr 공구만 보여요.'))
    await router.navigate('/') // 헤더 "캠공" 링크처럼 학교 없이 이동
    await waitFor(() => expect(screen.getByTestId('school-status').textContent).toBe(''))
  })

  test('재검수 3: 로그인 확인이 실패한 상태에서 헤더의 "로그인 다시 확인"으로 빠져나온다', async () => {
    mockState.sessionMemberId = 1
    server.use(http.get('/api/auth/me', () => new HttpResponse(null, { status: 503 }), { once: true }))
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: '로그인 다시 확인' }))
    await screen.findByRole('button', { name: '로그아웃' })
    await screen.findByText('우리 학교 · school.ac.kr')
  })
})
