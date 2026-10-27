import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { visibleActions } from './features/groupbuy/detail/visibleActions'
import { server } from './mocks/server'
import { mockState } from './mocks/state'
import { post } from './shared/api/client'
import { ApiError } from './shared/api/client'
import { errorMessage } from './shared/lib/errorMessage'
import { toKstLocalInput } from './shared/lib/time'
import { seedGroupBuy } from './test/factories'
import { renderApp } from './test/utils'

// 독립 검수(2026-10-28)에서 나온 항목들의 회귀 테스트.

describe('개설자 COMPLETED 상세 (검수 2)', () => {
  test('visibleActions: 입금·수령 상태는 참여자에게만', () => {
    const d = { status: 'COMPLETED', isOrganizer: true, myParticipation: { quantity: 3, amount: 3600 }, openChatUrl: null, participants: [] } as never
    expect(visibleActions(d).myConfirm).toBe(false)
  })
  test('개설자 상세에는 입금·수령 확인 문구가 없다', async () => {
    seedGroupBuy({ status: 'COMPLETED' })
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1')
    await screen.findByRole('link', { name: '참여자 체크' })
    expect(screen.queryByText(/입금 확인/)).toBeNull()
    expect(screen.queryByText(/수령 확인/)).toBeNull()
  })
})

describe('로그아웃 실패 (검수 3)', () => {
  test('서버 로그아웃이 실패하면 안내하고 로그인 상태를 유지한다', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 1
    server.use(http.post('/api/auth/logout', () => new HttpResponse(null, { status: 500 })))
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: '로그아웃' }))
    await screen.findByText('로그아웃을 서버에 알리지 못했어요. 다시 시도해 주세요.')
    screen.getByRole('button', { name: '로그아웃' })
  })
})

describe('비밀번호는 mutation 캐시에 남지 않는다 (검수 4)', () => {
  test('로그인', async () => {
    const { client } = renderApp('/login')
    await userEvent.type(await screen.findByLabelText('이메일'), 'organizer@school.ac.kr')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))
    await screen.findByRole('button', { name: '로그아웃' })
    await waitFor(() => expect(client.getMutationCache().getAll().filter((m) => JSON.stringify(m.state.variables ?? '').includes('password1234'))).toHaveLength(0))
  })
  test('인증', async () => {
    await post('/api/auth/signup', { email: 'new@school.ac.kr' })
    const { client } = renderApp(`/verify?token=${mockState.pending[0]!.token}`)
    await userEvent.type(await screen.findByLabelText('표시 이름'), '새내기')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234')
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: '인증 완료하기' }))
    await screen.findByRole('button', { name: '로그아웃' })
    await waitFor(() => expect(client.getMutationCache().getAll().filter((m) => JSON.stringify(m.state.variables ?? '').includes('password1234'))).toHaveLength(0))
  })
})

describe('지난 retryAt (검수 8)', () => {
  test('이미 지난 retryAt이면 남은 시간을 덧붙이지 않는다', () => {
    const e = new ApiError(429, 'TOO_MANY_REQUESTS', undefined, [], { retryAt: new Date(Date.now() - 60_000).toISOString() })
    expect(errorMessage(e)).not.toContain('마감됨')
    expect(errorMessage(e)).toBe('잠시 뒤에 다시 시도해 주세요.')
  })
  test('로그인 화면에서 이메일을 입력하면 재발송 입력란에도 반영된다', async () => {
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('이메일'), 'a@school.ac.kr')
    expect((screen.getByLabelText('인증 메일 받을 이메일') as HTMLInputElement).value).toBe('a@school.ac.kr')
  })
})

describe('만료 안내는 한 번만 (검수 9)', () => {
  test('로그인 화면에서 한 번 보여준 뒤에는 다시 열어도 나오지 않는다', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 1
    const { client, router } = renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    mockState.sessionExpired = true
    await client.invalidateQueries({ queryKey: ['groupbuy'] })
    await screen.findByText('로그인이 필요해요. 입력하던 내용은 저장되지 않아요.')
    await router.navigate('/signup')
    await screen.findByRole('heading', { name: '가입' })
    await router.navigate('/login')
    await screen.findByRole('heading', { name: '로그인' })
    expect(screen.queryByText('로그인이 필요해요. 입력하던 내용은 저장되지 않아요.')).toBeNull()
  })
})

describe('id가 정수가 아니면 요청하지 않는다 (검수 10)', () => {
  test.each(['/groupbuys/abc', '/groupbuys/abc/check', '/groupbuys/abc/edit'])('%s', async (path) => {
    mockState.sessionMemberId = 1
    const seen: string[] = []
    server.events.on('request:start', ({ request }) => void seen.push(new URL(request.url).pathname))
    renderApp(path)
    await screen.findByText(/찾을 수 없는 공구예요/)
    await new Promise((r) => setTimeout(r, 100))
    expect(seen.filter((p) => p.includes('/api/groupbuys/'))).toEqual([])
    server.events.removeAllListeners()
  })
})

describe('ALREADY_REGISTERED는 로그인 링크 (검수 11)', () => {
  test('인증 화면', async () => {
    await post('/api/auth/signup', { email: 'new@school.ac.kr' })
    server.use(http.post('/api/auth/verify', () => HttpResponse.json({ title: 'ALREADY_REGISTERED', status: 409 }, { status: 409 })))
    renderApp(`/verify?token=${mockState.pending[0]!.token}`)
    await userEvent.type(await screen.findByLabelText('표시 이름'), '새내기')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234')
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: '인증 완료하기' }))
    await screen.findByText('이미 가입된 이메일이에요. 로그인해 주세요.')
    expect(screen.getByRole('link', { name: '로그인' }).getAttribute('href')).toBe('/login')
  })
  test('가입 화면', async () => {
    renderApp('/signup')
    await userEvent.type(await screen.findByLabelText('이메일'), 'organizer@school.ac.kr')
    await userEvent.click(screen.getByRole('button', { name: '가입하기' }))
    await screen.findByText('이미 가입된 이메일이에요. 로그인해 주세요.')
    expect(screen.getAllByRole('link', { name: '로그인' }).some((a) => a.getAttribute('href') === '/login')).toBe(true)
  })
})

describe('방어와 경합 (검수 12)', () => {
  test('목록에 같은 id가 두 번 와도 한 번만 보인다', async () => {
    const c = { title: '중복 공구', itemName: 'x', category: 'FOOD', unitPrice: 1000, securedQuantity: 1, totalQuantity: 5, deadline: '2099-11-01T18:00:00+09:00', status: 'OPEN' }
    server.use(
      http.get('/api/groupbuys', ({ request }) =>
        new URL(request.url).searchParams.get('cursor')
          ? HttpResponse.json({ items: [{ id: 1, ...c }, { id: 2, ...c, title: '다음 공구' }], nextCursor: null })
          : HttpResponse.json({ items: [{ id: 1, ...c }], nextCursor: 'c1' }),
      ),
    )
    renderApp('/')
    await screen.findByText('중복 공구')
    await userEvent.click(screen.getByRole('button', { name: '더 보기' }))
    await screen.findByText('다음 공구')
    expect(screen.getAllByText('중복 공구')).toHaveLength(1)
  })

  test('조건을 빠르게 바꾸면 늦게 온 옛 응답이 화면을 덮지 않는다', async () => {
    const base = { itemName: 'x', unitPrice: 1000, securedQuantity: 1, totalQuantity: 5, deadline: '2099-11-01T18:00:00+09:00', status: 'OPEN' }
    server.use(
      http.get('/api/groupbuys', async ({ request }) => {
        const cat = new URL(request.url).searchParams.get('category')
        if (cat === 'FOOD') {
          await delay(300)
          return HttpResponse.json({ items: [{ id: 1, title: '식품 공구', category: 'FOOD', ...base }], nextCursor: null })
        }
        if (cat === 'DAILY') return HttpResponse.json({ items: [{ id: 2, title: '생활 공구', category: 'DAILY', ...base }], nextCursor: null })
        return HttpResponse.json({ items: [], nextCursor: null })
      }),
    )
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: '식품' }))
    await userEvent.click(screen.getByRole('button', { name: '생활용품' }))
    await screen.findByText('생활 공구')
    await new Promise((r) => setTimeout(r, 400))
    expect(screen.queryByText('식품 공구')).toBeNull()
    screen.getByText('생활 공구')
  })

  test('32. 폼 제출 중 서버 오류(5xx)면 일반 안내가 보이고 입력값이 유지된다', async () => {
    mockState.sessionMemberId = 1
    server.use(http.post('/api/groupbuys', () => new HttpResponse(null, { status: 500 })))
    renderApp('/groupbuys/new')
    await userEvent.type(await screen.findByLabelText('제목'), '입력 유지 공구')
    await userEvent.type(screen.getByLabelText('개당 가격'), '1200')
    await userEvent.type(screen.getByLabelText('총 수량'), '24')
    await userEvent.type(screen.getByLabelText('내 수량'), '9')
    await userEvent.selectOptions(screen.getByLabelText('카테고리'), 'FOOD')
    await userEvent.type(screen.getByLabelText('상품명'), '햇반')
    fireEvent.change(screen.getByLabelText('마감 (한국 시각)'), { target: { value: toKstLocalInput(new Date(Date.now() + 2 * 86_400_000).toISOString()) } })
    await userEvent.click(screen.getByRole('button', { name: '공구 열기' }))
    await screen.findByText('잠시 문제가 생겼어요. 다시 시도해 주세요.')
    expect((screen.getByLabelText('제목') as HTMLInputElement).value).toBe('입력 유지 공구')
    expect((screen.getByLabelText('개당 가격') as HTMLInputElement).value).toBe('1200')
  })

  test('401이면 mutation 캐시도 비운다', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 3 // 비참여자
    const { client } = renderApp('/groupbuys/1')
    await userEvent.click(await screen.findByRole('button', { name: '참여하기' }))
    mockState.sessionExpired = true
    await userEvent.type(screen.getByLabelText('수량'), '2')
    await userEvent.click(screen.getByRole('button', { name: '참여 신청' }))
    await screen.findByRole('heading', { name: '로그인' })
    expect(client.getMutationCache().getAll()).toHaveLength(0)
  })
})

describe('개설자 myParticipation이 null (검수 13)', () => {
  test('상세는 깨지지 않는다', async () => {
    seedGroupBuy({ participations: [{ memberId: 2, quantity: 6 }] }) // 개설자(1) 참여 행 없음
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    screen.getByRole('link', { name: '수정' })
  })
  test('수정 폼은 내 수량을 못 읽었다는 안내를 보인다', async () => {
    seedGroupBuy({ participations: [{ memberId: 2, quantity: 6 }] })
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1/edit')
    await screen.findByLabelText('내 수량')
    expect((screen.getByLabelText('내 수량') as HTMLInputElement).value).toBe('')
    screen.getByText('내 수량을 불러오지 못했어요. 직접 입력해 주세요.')
  })
})

