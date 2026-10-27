import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { server } from '../../../mocks/server'
import type { GroupBuyCard } from '../../../shared/api/types'
import { formatKst } from '../../../shared/lib/time'
import { renderApp } from '../../../test/utils'

const card = (id: number, over: Partial<GroupBuyCard> = {}): GroupBuyCard => ({
  id,
  title: `공구 ${id}`,
  itemName: `상품 ${id}`,
  category: 'FOOD',
  unitPrice: 1200,
  securedQuantity: 9,
  totalQuantity: 24,
  deadline: '2099-11-01T18:00:00+09:00',
  status: 'OPEN',
  ...over,
})
const page = (items: GroupBuyCard[], nextCursor: string | null = null) => HttpResponse.json({ items, nextCursor })

describe('GroupBuyList (US1)', () => {
  test('1. 카드에 필드가 모두 표시되고 비로그인으로 열린다', async () => {
    server.use(http.get('/api/groupbuys', () => page([card(1)])))
    renderApp('/')
    const link = await screen.findByRole('link', { name: /공구 1/ })
    expect(link.getAttribute('href')).toBe('/groupbuys/1')
    const c = within(link)
    c.getByText('공구 1')
    c.getByText('상품 1')
    c.getByText(/1,200원/)
    c.getByText('9/24')
    expect(c.getByRole('progressbar').getAttribute('aria-valuenow')).toBe('9')
    c.getByText(formatKst('2099-11-01T18:00:00+09:00'))
    c.getByText(/남음/)
    c.getByText('모집 중')
    c.getByText('식품')
  })

  test('2. 더 보기로 이어지고 마지막에 버튼이 사라진다', async () => {
    const cursors: (string | null)[] = []
    server.use(
      http.get('/api/groupbuys', ({ request }) => {
        const cursor = new URL(request.url).searchParams.get('cursor')
        cursors.push(cursor)
        return cursor
          ? page([card(21), card(22)])
          : page(Array.from({ length: 20 }, (_, i) => card(i + 1)), 'next-1')
      }),
    )
    renderApp('/')
    await screen.findByRole('link', { name: /공구 20/ })
    await userEvent.click(screen.getByRole('button', { name: '더 보기' }))
    await screen.findByRole('link', { name: /공구 22/ })
    expect(screen.getAllByRole('link', { name: /공구 \d+/ })).toHaveLength(22)
    expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull()
    expect(cursors).toEqual([null, 'next-1'])
  })

  test('3. 조건 변경은 주소에 반영되고 커서 없이 처음부터 다시 불러온다', async () => {
    const urls: URL[] = []
    server.use(
      http.get('/api/groupbuys', ({ request }) => {
        const u = new URL(request.url)
        urls.push(u)
        return u.searchParams.get('category') ? page([card(7)]) : page([card(1)], 'next-1')
      }),
    )
    const { router } = renderApp('/')
    await screen.findByRole('link', { name: /공구 1/ })
    await userEvent.click(screen.getByRole('button', { name: '더 보기' }))
    await userEvent.click(screen.getByRole('button', { name: '식품' }))
    await screen.findByRole('link', { name: /공구 7/ })
    expect(router.state.location.search).toContain('category=FOOD')
    const last = urls.at(-1)!
    expect(last.searchParams.get('category')).toBe('FOOD')
    expect(last.searchParams.get('cursor')).toBeNull()
    await userEvent.selectOptions(screen.getByLabelText('정렬'), 'deadline')
    await waitFor(() => expect(router.state.location.search).toContain('sort=deadline'))
  })

  test('3b. 검색어는 디바운스 후 주소에 반영된다', async () => {
    const qs: (string | null)[] = []
    server.use(http.get('/api/groupbuys', ({ request }) => (qs.push(new URL(request.url).searchParams.get('q')), page([card(1)]))))
    const { router } = renderApp('/')
    await screen.findByRole('link', { name: /공구 1/ })
    await userEvent.type(screen.getByRole('searchbox'), '햇반')
    await waitFor(() => expect(router.state.location.search).toContain('q='))
    expect(decodeURIComponent(router.state.location.search)).toContain('q=햇반')
    await waitFor(() => expect(qs).toHaveLength(2))
    // 글자마다 요청하지 않는다: 초기 1번 + 디바운스 후 1번
    expect(qs).toEqual([null, '햇반'])
  })

  test('3c. 검색어 입력 직후 카테고리를 바꿔도 두 조건이 모두 주소에 남는다', async () => {
    server.use(http.get('/api/groupbuys', () => page([card(1)])))
    const { router } = renderApp('/')
    await screen.findByRole('link', { name: /공구 1/ })
    await userEvent.type(screen.getByRole('searchbox'), '햇반')
    await userEvent.click(screen.getByRole('button', { name: '식품' })) // 디바운스가 끝나기 전
    await waitFor(() => expect(decodeURIComponent(router.state.location.search)).toContain('q=햇반'))
    expect(router.state.location.search).toContain('category=FOOD')
  })

  test('4. 빈 목록과 검색 결과 없음은 문구가 다르다', async () => {
    server.use(http.get('/api/groupbuys', () => page([])))
    const a = renderApp('/')
    await screen.findByText('아직 올라온 공구가 없어요.')
    a.unmount()
    const b = renderApp('/?q=없는물건')
    await screen.findByText('검색 결과가 없어요. 다른 검색어나 조건을 써보세요.')
    b.unmount()
    // FR-014: 카테고리·기본이 아닌 상태도 조건이다. 정렬은 조건이 아니다.
    const c = renderApp('/?category=FOOD')
    await screen.findByText('검색 결과가 없어요. 다른 검색어나 조건을 써보세요.')
    c.unmount()
    const d = renderApp('/?status=FAILED')
    await screen.findByText('검색 결과가 없어요. 다른 검색어나 조건을 써보세요.')
    d.unmount()
    renderApp('/?sort=deadline')
    await screen.findByText('아직 올라온 공구가 없어요.')
  })

  test('5. PENDING_CLOSE는 마감됨(확정 대기) 뱃지', async () => {
    server.use(http.get('/api/groupbuys', () => page([card(1, { status: 'PENDING_CLOSE' })])))
    renderApp('/?status=PENDING_CLOSE')
    await screen.findByText('마감됨(확정 대기)')
  })

  test('6. 알 수 없는 status는 일반 표시이고 화면이 멈추지 않는다', async () => {
    server.use(http.get('/api/groupbuys', () => page([card(1, { status: 'SOMETHING_NEW' }), card(2)])))
    renderApp('/')
    await screen.findByText('알 수 없음')
    screen.getByRole('link', { name: /공구 2/ })
  })

  test('FR-007. 로딩 → 오류(다시 시도) → 정상을 구분한다', async () => {
    let fail = true
    server.use(
      http.get('/api/groupbuys', async () => {
        await delay(50)
        return fail ? new HttpResponse(null, { status: 500 }) : page([card(1)])
      }),
    )
    renderApp('/')
    await screen.findByRole('status') // 로딩
    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('잠시 문제가 생겼어요')
    fail = false
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    await screen.findByRole('link', { name: /공구 1/ })
  })

  test('잘못된 주소 값은 기본 조건으로 되돌려 요청한다', async () => {
    const urls: URL[] = []
    server.use(http.get('/api/groupbuys', ({ request }) => (urls.push(new URL(request.url)), page([card(1)]))))
    renderApp('/?status=NOPE&sort=zzz&category=XXX')
    await screen.findByRole('link', { name: /공구 1/ })
    expect(urls[0]!.searchParams.get('status')).toBe('OPEN')
    expect(urls[0]!.searchParams.get('sort')).toBe('latest')
    expect(urls[0]!.searchParams.get('category')).toBeNull()
  })
})
