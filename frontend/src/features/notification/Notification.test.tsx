import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, test } from 'vitest'
import { server } from '../../mocks/server'
import { mockState, type MockNotification } from '../../mocks/state'
import { seedGroupBuy } from '../../test/factories'
import { renderApp } from '../../test/utils'

let nextId = 1
const note = (over: Partial<MockNotification> = {}): MockNotification => ({
  id: nextId++,
  memberId: 2,
  type: 'GROUPBUY_COMPLETED',
  groupBuyId: 1,
  groupBuyTitle: '햇반 24개 공구',
  changedFields: null,
  createdAt: '2026-10-27T10:00:00+09:00',
  read: false,
  linkable: true,
  ...over,
})
const asParticipant = (...n: MockNotification[]) => {
  seedGroupBuy()
  mockState.sessionMemberId = 2
  mockState.notifications = n
}
const badge = () => screen.queryByLabelText(/읽지 않은 알림 \d+개/)

describe('알림 (US7)', () => {
  afterEach(() => server.events.removeAllListeners())

  test('헤더 배지에 읽지 않은 수, 0이면 숨김', async () => {
    asParticipant(note(), note(), note({ read: true }))
    const a = renderApp('/')
    await waitFor(() => expect(badge()?.textContent).toBe('2'))
    a.unmount()
    asParticipant(note({ read: true }))
    renderApp('/')
    await screen.findByRole('link', { name: /알림/ })
    await new Promise((r) => setTimeout(r, 100)) // 조회가 끝난 뒤에도 0이면 숨김
    expect(badge()).toBeNull()
  })

  test('읽지 않은 수는 화면을 불러올 때 한 번만 조회한다(폴링 없음)', async () => {
    asParticipant(note())
    let calls = 0
    server.events.on('request:start', ({ request }) => {
      if (new URL(request.url).pathname === '/api/notifications/unread-count') calls++
    })
    renderApp('/')
    await waitFor(() => expect(badge()).not.toBeNull())
    await new Promise((r) => setTimeout(r, 300))
    expect(calls).toBe(1)
  })

  test('조회가 실패하면 배지만 조용히 숨기고 화면은 멀쩡하다', async () => {
    asParticipant(note())
    server.use(http.get('/api/notifications/unread-count', () => new HttpResponse(null, { status: 500 })))
    renderApp('/')
    await screen.findByRole('link', { name: /알림/ })
    await new Promise((r) => setTimeout(r, 100))
    expect(badge()).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  test('유형별 문구와 시각', async () => {
    asParticipant(
      note({ type: 'GROUPBUY_COMPLETED' }),
      note({ type: 'GROUPBUY_FAILED', groupBuyTitle: '실패 공구' }),
      note({ type: 'GROUPBUY_CHANGED', groupBuyTitle: '수정 공구', changedFields: ['unitPrice', 'deadline', 'itemUrl', 'organizerQuantity'] }),
      note({ type: 'GROUPBUY_CANCELLED', groupBuyTitle: '취소 공구', linkable: false }),
    )
    renderApp('/notifications')
    await screen.findByText('「햇반 24개 공구」 모집이 완료됐어요.')
    screen.getByText('「실패 공구」 모집이 실패했어요.')
    screen.getByText('「수정 공구」 정보가 수정됐어요.')
    screen.getByText('바뀐 항목: 개당 가격, 마감, 구매처 링크, 개설자 수량')
    screen.getByText('「취소 공구」 공구가 취소됐어요.')
    expect(screen.getAllByText('2026-10-27 10:00').length).toBe(4)
  })

  test('알 수 없는 바뀐 항목 이름은 그대로 두어 화면이 멈추지 않는다', async () => {
    asParticipant(note({ type: 'GROUPBUY_CHANGED', changedFields: ['somethingNew'] }))
    renderApp('/notifications')
    await screen.findByText('바뀐 항목: somethingNew')
  })

  test('눌러 읽음 처리 → 배지 감소 → 상세로 이동', async () => {
    asParticipant(note({ id: 100 }), note({ id: 101, groupBuyId: 1 }))
    const { router } = renderApp('/notifications')
    await waitFor(() => expect(badge()?.textContent).toBe('2'))
    const items = await screen.findAllByRole('button', { name: /모집이 완료/ })
    await userEvent.click(items[0]!)
    await waitFor(() => expect(router.state.location.pathname).toBe('/groupbuys/1'))
    expect(mockState.notifications.filter((n) => n.read)).toHaveLength(1)
    await waitFor(() => expect(badge()?.textContent).toBe('1'))
  })

  test('linkable false는 이동하지 않고 내용만 보이며 읽음 처리는 된다', async () => {
    asParticipant(note({ type: 'GROUPBUY_CANCELLED', groupBuyTitle: '취소 공구', linkable: false }))
    const { router } = renderApp('/notifications')
    await userEvent.click(await screen.findByRole('button', { name: /취소됐어요/ }))
    await waitFor(() => expect(mockState.notifications[0]!.read).toBe(true))
    expect(router.state.location.pathname).toBe('/notifications')
    await waitFor(() => expect(badge()).toBeNull())
  })

  test('더 보기로 다음 쪽을 이어 붙인다', async () => {
    asParticipant(...Array.from({ length: 25 }, () => note({ read: true })))
    renderApp('/notifications')
    await screen.findAllByRole('listitem')
    expect(screen.getAllByRole('listitem')).toHaveLength(20)
    await userEvent.click(screen.getByRole('button', { name: '더 보기' }))
    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(25))
    expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull()
  })

  test('알림이 없으면 빈 상태 문구', async () => {
    asParticipant()
    renderApp('/notifications')
    await screen.findByText('아직 알림이 없어요.')
  })

  test('남의 알림은 목록에 없다', async () => {
    asParticipant(note({ memberId: 3, groupBuyTitle: '남의 공구' }))
    renderApp('/notifications')
    await screen.findByText('아직 알림이 없어요.')
  })

  test('로그아웃하면 배지가 사라진다', async () => {
    asParticipant(note())
    renderApp('/')
    await waitFor(() => expect(badge()).not.toBeNull())
    await userEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    await waitFor(() => expect(badge()).toBeNull())
    expect(within(document.body).queryByRole('link', { name: /알림/ })).toBeNull()
  })
})
