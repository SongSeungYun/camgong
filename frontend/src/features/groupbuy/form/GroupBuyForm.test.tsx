import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { mockState } from '../../../mocks/state'
import { server } from '../../../mocks/server'
import { toKstLocalInput } from '../../../shared/lib/time'
import { seedGroupBuy } from '../../../test/factories'
import { renderApp } from '../../../test/utils'

const inDays = (n: number) => toKstLocalInput(new Date(Date.now() + n * 86_400_000).toISOString())

async function fillForm(over: Record<string, string> = {}) {
  const v = { title: '햇반 24개 공구', itemName: '햇반 210g', itemUrl: 'https://example.com/a', unitPrice: '1200', totalQuantity: '24', organizerQuantity: '9', deadline: inDays(2), openChatUrl: 'https://open.kakao.com/o/x', ...over }
  await userEvent.type(screen.getByLabelText('제목'), v.title)
  await userEvent.selectOptions(screen.getByLabelText('카테고리'), 'FOOD')
  await userEvent.type(screen.getByLabelText('상품명'), v.itemName)
  await userEvent.type(screen.getByLabelText('구매처 링크'), v.itemUrl)
  await userEvent.type(screen.getByLabelText('개당 가격'), v.unitPrice)
  await userEvent.type(screen.getByLabelText('총 수량'), v.totalQuantity)
  await userEvent.type(screen.getByLabelText('내 수량'), v.organizerQuantity)
  fireEvent.change(screen.getByLabelText('마감 (한국 시각)'), { target: { value: v.deadline } })
  await userEvent.type(screen.getByLabelText('오픈채팅 링크'), v.openChatUrl)
}

const asOrganizer = () => {
  mockState.sessionMemberId = 1
}

describe('개설 폼 (US3)', () => {
  test('구매처·오픈채팅 링크는 필수임을 안내한다(001 계약: 필수)', async () => {
    asOrganizer()
    renderApp('/groupbuys/new')
    await screen.findByRole('heading', { name: '공구 열기' })
    screen.getByText('필수예요. https 주소만 쓸 수 있어요.')
    screen.getByText('필수예요. https 주소만 쓸 수 있어요. 모집이 끝나면 참여자에게만 보여요.')
  })

  test('14. 내 부담 금액을 미리 보여준다(1,200 × 9 = 10,800원)', async () => {
    asOrganizer()
    renderApp('/groupbuys/new')
    await screen.findByRole('heading', { name: '공구 열기' })
    await userEvent.type(screen.getByLabelText('개당 가격'), '1200')
    await userEvent.type(screen.getByLabelText('총 수량'), '24')
    await userEvent.type(screen.getByLabelText('내 수량'), '9')
    screen.getByText('내 부담 금액 10,800원')
  })

  test('15. 마감은 KST로 입력해 +09:00 오프셋 ISO로 전송된다(기기 시간대 UTC)', async () => {
    asOrganizer()
    let body: Record<string, unknown> = {}
    server.use(
      http.post('/api/groupbuys', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: 77 }, { status: 201 })
      }),
    )
    renderApp('/groupbuys/new')
    await screen.findByRole('heading', { name: '공구 열기' })
    await fillForm({ deadline: '2099-11-01T18:00' })
    await userEvent.click(screen.getByRole('button', { name: '공구 열기' }))
    await waitFor(() => expect(body.deadline).toBe('2099-11-01T18:00:00+09:00'))
    expect(body).toMatchObject({ unitPrice: 1200, totalQuantity: 24, organizerQuantity: 9, category: 'FOOD' })
  })

  test('16. 서버 400 errors[]가 해당 필드 옆에 한 번에 보이고 입력값은 유지된다', async () => {
    asOrganizer()
    renderApp('/groupbuys/new')
    await screen.findByRole('heading', { name: '공구 열기' })
    await fillForm({ unitPrice: '2000000', totalQuantity: '1', organizerQuantity: '0', itemUrl: 'http://insecure.example.com', deadline: inDays(-1) })
    await userEvent.click(screen.getByRole('button', { name: '공구 열기' }))
    const err = (label: string) => {
      const el = screen.getByLabelText(label)
      return document.getElementById(el.getAttribute('aria-describedby') ?? '')?.textContent
    }
    await waitFor(() => expect(err('개당 가격')).toBeTruthy())
    expect(err('총 수량')).toBeTruthy()
    expect(err('내 수량')).toBeTruthy()
    expect(err('구매처 링크')).toBeTruthy()
    expect(err('마감 (한국 시각)')).toBeTruthy()
    expect(err('제목')).toBeUndefined() // 맞는 필드에는 오류가 없다
    expect((screen.getByLabelText('개당 가격') as HTMLInputElement).value).toBe('2000000') // 입력값 유지
    expect((screen.getByLabelText('제목') as HTMLInputElement).value).toBe('햇반 24개 공구')
  })

  test('16b. 폼에 없는 필드 오류는 폼 상단에 모인다', async () => {
    asOrganizer()
    server.use(
      http.post('/api/groupbuys', () =>
        HttpResponse.json({ title: 'Bad Request', status: 400, errors: [{ field: 'title', message: '제목이 이상해요' }, { field: 'mystery', message: '알 수 없는 필드 오류' }] }, { status: 400 }),
      ),
    )
    renderApp('/groupbuys/new')
    await screen.findByRole('heading', { name: '공구 열기' })
    await fillForm()
    await userEvent.click(screen.getByRole('button', { name: '공구 열기' }))
    await screen.findByText('알 수 없는 필드 오류')
    const title = screen.getByLabelText('제목')
    expect(document.getElementById(title.getAttribute('aria-describedby')!)!.textContent).toBe('제목이 이상해요')
  })

  test('17. 개설에 성공하면 새 상세로 이동한다', async () => {
    asOrganizer()
    const { router } = renderApp('/groupbuys/new')
    await screen.findByRole('heading', { name: '공구 열기' })
    await fillForm({ title: '새로 연 공구' })
    await userEvent.click(screen.getByRole('button', { name: '공구 열기' }))
    await screen.findByRole('heading', { name: '새로 연 공구' })
    expect(router.state.location.pathname).toMatch(/^\/groupbuys\/\d+$/)
  })

  test('제출 중에는 버튼이 비활성화되어 한 번만 전송된다', async () => {
    asOrganizer()
    let calls = 0
    server.use(
      http.post('/api/groupbuys', async () => {
        calls++
        await delay(100)
        return HttpResponse.json({ id: 77 }, { status: 201 })
      }),
    )
    renderApp('/groupbuys/new')
    await screen.findByRole('heading', { name: '공구 열기' })
    await fillForm()
    const btn = screen.getByRole('button', { name: '공구 열기' }) as HTMLButtonElement
    await userEvent.click(btn)
    await userEvent.click(btn)
    expect(btn.disabled).toBe(true)
    await waitFor(() => expect(calls).toBe(1))
  })

  test('중복 요청(409 DUPLICATE_REQUEST)은 안내한다', async () => {
    asOrganizer()
    server.use(http.post('/api/groupbuys', () => HttpResponse.json({ title: 'DUPLICATE_REQUEST', status: 409 }, { status: 409 })))
    renderApp('/groupbuys/new')
    await screen.findByRole('heading', { name: '공구 열기' })
    await fillForm()
    await userEvent.click(screen.getByRole('button', { name: '공구 열기' }))
    await screen.findByText('같은 공구가 이미 열려 있어요.')
  })
})

describe('수정 폼', () => {
  test('18. 현재 값이 채워지고 마감 연장만/총 수량 ≥ 확보 안내가 보인다', async () => {
    const g = seedGroupBuy({ participations: [{ memberId: 1, quantity: 3 }, { memberId: 2, quantity: 6 }] })
    asOrganizer()
    renderApp('/groupbuys/1/edit')
    await screen.findByRole('heading', { name: '공구 수정' })
    await waitFor(() => expect((screen.getByLabelText('제목') as HTMLInputElement).value).toBe('햇반 24개 공구'))
    expect((screen.getByLabelText('내 수량') as HTMLInputElement).value).toBe('3')
    expect((screen.getByLabelText('마감 (한국 시각)') as HTMLInputElement).value).toBe(toKstLocalInput(g.deadline))
    expect((screen.getByLabelText('오픈채팅 링크') as HTMLInputElement).value).toBe('')
    screen.getByText(/마감은 늘리기만 할 수 있어요/)
    screen.getByText(/총 수량은 확보 수량\(9개\) 이상이어야 해요/)
  })

  test('수정에 성공하면 상세로 돌아간다(총 수량 < 확보는 필드 오류)', async () => {
    seedGroupBuy({ openChatUrl: 'https://open.kakao.com/o/abc' })
    asOrganizer()
    const { router } = renderApp('/groupbuys/1/edit')
    await waitFor(() => expect((screen.getByLabelText('제목') as HTMLInputElement).value).toBe('햇반 24개 공구'))
    const total = screen.getByLabelText('총 수량')
    await userEvent.clear(total)
    await userEvent.type(total, '5')
    await userEvent.click(screen.getByRole('button', { name: '수정 저장' }))
    await waitFor(() => expect(document.getElementById(total.getAttribute('aria-describedby') ?? '')?.textContent).toBeTruthy())
    await userEvent.clear(total)
    await userEvent.type(total, '30')
    await userEvent.click(screen.getByRole('button', { name: '수정 저장' }))
    await screen.findByText(/총 30개/)
    expect(router.state.location.pathname).toBe('/groupbuys/1')
  })

  test('개설자가 아니면 수정 화면을 거부한다', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 2
    renderApp('/groupbuys/1/edit')
    await screen.findByText('개설자만 쓸 수 있어요.')
    expect(screen.queryByRole('button', { name: '수정 저장' })).toBeNull()
  })
})

describe('취소·권한', () => {
  test('19. 취소는 확인 대화상자를 거친 뒤에만 실행되고 목록으로 이동한다', async () => {
    seedGroupBuy()
    asOrganizer()
    let cancelled = 0
    server.events.on('request:start', ({ request }) => {
      if (request.url.endsWith('/cancel')) cancelled++
    })
    const { router } = renderApp('/groupbuys/1')
    await userEvent.click(await screen.findByRole('button', { name: '공구 취소' }))
    const dialog = screen.getByRole('dialog')
    expect(cancelled).toBe(0)
    await userEvent.click(within(dialog).getByRole('button', { name: '돌아가기' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(cancelled).toBe(0)
    await userEvent.click(screen.getByRole('button', { name: '공구 취소' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '공구 취소하기' }))
    await screen.findByText('공구를 취소했어요. 목록에서 사라졌어요.')
    expect(router.state.location.pathname).toBe('/')
    expect(cancelled).toBe(1)
    expect(mockState.groupBuys).toHaveLength(0)
    server.events.removeAllListeners()
  })

  test('취소가 NOT_MODIFIABLE로 거부되면 사유 문구를 보인다', async () => {
    seedGroupBuy()
    asOrganizer()
    server.use(http.post('/api/groupbuys/1/cancel', () => HttpResponse.json({ title: 'NOT_MODIFIABLE', status: 409, reason: 'DEADLINE_PASSED' }, { status: 409 })))
    renderApp('/groupbuys/1')
    await userEvent.click(await screen.findByRole('button', { name: '공구 취소' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '공구 취소하기' }))
    await screen.findByText('이미 모집이 끝났거나 마감이 지나서 수정·취소할 수 없어요.')
  })

  test.each([[2], [3]])('20. 개설자가 아닌 회원(%i)에게는 수정·취소 버튼이 없다', async (id) => {
    seedGroupBuy()
    mockState.sessionMemberId = id
    renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    expect(screen.queryByRole('link', { name: '수정' })).toBeNull()
    expect(screen.queryByRole('button', { name: '공구 취소' })).toBeNull()
  })
})
