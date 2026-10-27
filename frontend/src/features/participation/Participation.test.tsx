import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { server } from '../../mocks/server'
import { mockState } from '../../mocks/state'
import { seedGroupBuy } from '../../test/factories'
import { renderApp } from '../../test/utils'

// seedGroupBuy: 총 24, 개설자(1) 3, 참여자(2) 6 → 확보 9, 남은 15, 개당 1,200원. 비참여자=3.
const asOutsider = () => {
  seedGroupBuy()
  mockState.sessionMemberId = 3
}
async function openJoin() {
  await userEvent.click(await screen.findByRole('button', { name: '참여하기' }))
}
const qty = () => screen.getByLabelText('수량') as HTMLInputElement
async function typeQty(v: string) {
  await userEvent.clear(qty())
  await userEvent.type(qty(), v)
}
const submit = () => screen.getByRole('button', { name: '참여 신청' }) as HTMLButtonElement

describe('참여 (US4)', () => {
  test('21. 남은 수량 이하만 제출할 수 있고 금액을 미리 보여준다', async () => {
    asOutsider()
    renderApp('/groupbuys/1')
    await openJoin()
    await typeQty('16')
    expect(submit().disabled).toBe(true)
    screen.getByText('남은 수량(15개) 이하로 입력해 주세요.')
    await typeQty('4')
    screen.getByText('내 부담 금액 4,800원')
    expect(submit().disabled).toBe(false)
    await userEvent.click(submit())
    await screen.findByText('내 참여: 4개 · 4,800원')
    screen.getByRole('button', { name: '수량 변경' })
    screen.getByRole('button', { name: '참여 취소' })
    expect(screen.queryByRole('button', { name: '참여하기' })).toBeNull()
    screen.getByText(/확보 13개/)
  })

  test('22. 가격이 바뀌면 새 가격을 안내하고 다시 확인해야 제출된다', async () => {
    asOutsider()
    mockState.priceChangeTo = 1500
    renderApp('/groupbuys/1')
    await openJoin()
    await typeQty('4')
    await userEvent.click(submit())
    await screen.findByText('개당 가격이 바뀌었어요(1,500원). 금액을 다시 확인해 주세요.')
    expect(submit().disabled).toBe(true) // 재확인 전에는 제출 불가
    screen.getByText('내 부담 금액 6,000원') // 새 가격 기준
    await waitFor(() => expect(screen.getAllByText(/개당 1,500원/)).toHaveLength(2)) // 폼과 상세 모두 새 가격으로 갱신
    await userEvent.click(screen.getByRole('button', { name: '바뀐 가격 확인했어요' }))
    expect(submit().disabled).toBe(false)
    await userEvent.click(submit())
    await screen.findByText('내 참여: 4개 · 6,000원')
  })

  test('23. 수량 부족이면 최신 남은 수량을 안내하고 상세를 갱신한다', async () => {
    asOutsider()
    renderApp('/groupbuys/1')
    await openJoin()
    screen.getByText(/남은 15개/)
    mockState.groupBuys[0]!.participations[1]!.quantity = 18 // 그 사이 다른 사람이 참여: 남은 3개
    await typeQty('4')
    await userEvent.click(submit())
    await screen.findByText('남은 수량이 부족해요. 지금 남은 수량은 3개예요.')
    await screen.findByText(/남은 3개/)
  })

  test.each([
    ['DEADLINE_PASSED', '마감 시간이 지나서 참여할 수 없어요.', (g: (typeof mockState.groupBuys)[0]) => { g.deadline = new Date(Date.now() - 1000).toISOString() }],
    ['COMPLETED', '이미 모집이 끝난 공구예요.', (g: (typeof mockState.groupBuys)[0]) => { g.status = 'COMPLETED' }],
    ['FAILED', '모집에 실패한 공구예요.', (g: (typeof mockState.groupBuys)[0]) => { g.status = 'FAILED' }],
  ])('24. NOT_JOINABLE/%s는 각각 다른 문구이고 상세를 다시 불러온다', async (_r, message, mutate) => {
    asOutsider()
    renderApp('/groupbuys/1')
    await openJoin()
    mutate(mockState.groupBuys[0]!)
    await typeQty('2')
    await userEvent.click(submit())
    await screen.findByText(message)
    if (_r !== 'DEADLINE_PASSED') {
      await waitFor(() => expect(screen.queryByRole('button', { name: '참여하기' })).toBeNull()) // 상세가 갱신돼 참여 버튼이 사라짐
    }
  })

  test('개설자 거부 문구(ORGANIZER_NOT_ALLOWED)', async () => {
    asOutsider()
    server.use(http.post('/api/groupbuys/1/participations', () => HttpResponse.json({ title: 'ORGANIZER_NOT_ALLOWED', status: 403 }, { status: 403 })))
    renderApp('/groupbuys/1')
    await openJoin()
    await typeQty('2')
    await userEvent.click(submit())
    await screen.findByText('개설자는 직접 참여할 수 없어요. 수량은 공구 수정에서 바꿔요.')
  })

  test('25. 참여한 회원에게는 수량 변경·참여 취소만 보이고, 취소는 확인을 거친다', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 2
    let deletes = 0
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'DELETE') deletes++
    })
    renderApp('/groupbuys/1')
    await screen.findByRole('button', { name: '수량 변경' })
    expect(screen.queryByRole('button', { name: '참여하기' })).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: '참여 취소' }))
    const dialog = screen.getByRole('dialog')
    expect(deletes).toBe(0)
    await userEvent.click(within(dialog).getByRole('button', { name: '돌아가기' }))
    expect(deletes).toBe(0)
    await userEvent.click(screen.getByRole('button', { name: '참여 취소' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '참여 취소하기' }))
    await screen.findByText('참여 안 함')
    screen.getByRole('button', { name: '참여하기' })
    expect(deletes).toBe(1)
    server.events.removeAllListeners()
  })

  test('수량 변경: 현재 수량이 채워지고 새 수량으로 바뀐다', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 2
    renderApp('/groupbuys/1')
    await userEvent.click(await screen.findByRole('button', { name: '수량 변경' }))
    expect(qty().value).toBe('6')
    await typeQty('22') // 최대 = 남은 15 + 내 6 = 21
    screen.getByText('최대 21개까지 입력할 수 있어요.')
    await typeQty('10')
    await userEvent.click(screen.getByRole('button', { name: '수량 변경 저장' }))
    await screen.findByText('내 참여: 10개 · 12,000원')
  })

  test('26. 제출을 연타해도 요청은 1건이고 오류처럼 보이지 않는다', async () => {
    asOutsider()
    let posts = 0
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'POST' && request.url.endsWith('/participations')) posts++
    })
    renderApp('/groupbuys/1')
    await openJoin()
    await typeQty('2')
    await userEvent.dblClick(submit())
    await screen.findByText('내 참여: 2개 · 2,400원')
    expect(posts).toBe(1)
    expect(screen.queryByRole('alert')).toBeNull()
    server.events.removeAllListeners()
  })

  test('26b. 이미 반영된 같은 수량 재시도(200)도 성공으로 보인다', async () => {
    asOutsider()
    renderApp('/groupbuys/1')
    await openJoin()
    mockState.groupBuys[0]!.participations.push({ memberId: 3, quantity: 2 }) // 첫 요청이 이미 반영됨(재시도 상황)
    await typeQty('2')
    await userEvent.click(submit())
    await screen.findByText('내 참여: 2개 · 2,400원')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  test('27. 마지막 수량으로 참여하면 모집 완료가 되고 참여자에게 오픈채팅 링크가 보인다', async () => {
    seedGroupBuy({ openChatUrl: 'https://open.kakao.com/o/abc' })
    mockState.sessionMemberId = 3
    renderApp('/groupbuys/1')
    await openJoin()
    expect(screen.queryByRole('link', { name: /오픈채팅/ })).toBeNull()
    await typeQty('15')
    await userEvent.click(submit())
    await screen.findByText('모집 완료')
    const link = await screen.findByRole('link', { name: /오픈채팅 입장/ })
    expect(link.getAttribute('target')).toBe('_blank')
  })

  test('모집 종료·마감 후에는 참여 관련 버튼이 없다', async () => {
    seedGroupBuy({ status: 'FAILED' })
    mockState.sessionMemberId = 3
    renderApp('/groupbuys/1')
    await screen.findByText('모집 실패')
    for (const n of ['참여하기', '수량 변경', '참여 취소']) expect(screen.queryByRole('button', { name: n })).toBeNull()
  })
})
