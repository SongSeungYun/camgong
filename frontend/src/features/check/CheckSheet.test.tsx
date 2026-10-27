import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { server } from '../../mocks/server'
import { mockState } from '../../mocks/state'
import { seedGroupBuy } from '../../test/factories'
import { renderApp } from '../../test/utils'

// 모집 완료 공구: 개설자(1) 3개, 참여자(2) 6개 = 7,200원, 구경꾼(3) 4개 = 4,800원. 개당 1,200원.
const PAID_AT = '2026-10-27T10:00:00+09:00'
function seedCompleted(over: { paid2?: boolean; received3?: boolean } = {}) {
  seedGroupBuy({
    status: 'COMPLETED',
    totalQuantity: 13,
    participations: [
      { memberId: 1, quantity: 3 },
      { memberId: 2, quantity: 6, paidConfirmedAt: over.paid2 ? PAID_AT : null, receivedConfirmedAt: null },
      { memberId: 3, quantity: 4, paidConfirmedAt: null, receivedConfirmedAt: over.received3 ? PAID_AT : null },
    ],
  })
}
const row = (name: string) => screen.getByRole('listitem', { name })
const summary = () => screen.getByRole('region', { name: '요약' })

describe('체크표 (US6)', () => {
  test('개설자 상세(모집 완료)에서만 체크 링크가 보인다', async () => {
    seedCompleted()
    mockState.sessionMemberId = 1
    const a = renderApp('/groupbuys/1')
    await screen.findByRole('link', { name: '참여자 체크' })
    a.unmount()

    mockState.sessionMemberId = 2 // 참여자
    const b = renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    expect(screen.queryByRole('link', { name: '참여자 체크' })).toBeNull()
    b.unmount()

    seedGroupBuy() // 모집 중
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    expect(screen.queryByRole('link', { name: '참여자 체크' })).toBeNull()
  })

  test('행 필드와 요약을 보여주고 개설자 본인은 행에 없다', async () => {
    seedCompleted({ paid2: true })
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1/check')
    await screen.findByRole('listitem', { name: '참여자' })
    const r = within(row('참여자'))
    r.getByText('6개')
    r.getByText('7,200원')
    r.getByText(/입금 확인됨/)
    r.getByText('수령 확인 전')
    expect(screen.queryByRole('listitem', { name: '개설자' })).toBeNull()
    const s = within(summary())
    s.getByText('입금 완료 1명 / 2명')
    s.getByText('수령 완료 0명 / 2명')
    s.getByText('입금 확인 7,200원 / 12,000원')
  })

  test('필터는 목록만 거르고 요약은 전체 기준을 유지한다', async () => {
    seedCompleted({ paid2: true })
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1/check')
    await screen.findByRole('listitem', { name: '참여자' })
    await userEvent.click(screen.getByRole('button', { name: '미입금' }))
    await waitFor(() => expect(screen.queryByRole('listitem', { name: '참여자' })).toBeNull())
    screen.getByRole('listitem', { name: '구경꾼' })
    within(summary()).getByText('입금 완료 1명 / 2명')
    await userEvent.click(screen.getByRole('button', { name: '전체' }))
    await screen.findByRole('listitem', { name: '참여자' })
  })

  test('입금 지정과 되돌리기: 행과 요약이 갱신된다', async () => {
    seedCompleted()
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1/check')
    await screen.findByRole('listitem', { name: '참여자' })
    await userEvent.click(within(row('참여자')).getByRole('button', { name: '입금 확인' }))
    await within(row('참여자')).findByText(/입금 확인됨/)
    await waitFor(() => within(summary()).getByText('입금 완료 1명 / 2명'))
    within(summary()).getByText('입금 확인 7,200원 / 12,000원')
    await userEvent.click(within(row('참여자')).getByRole('button', { name: '입금 확인 취소' }))
    await within(row('참여자')).findByText('입금 확인 전')
    await waitFor(() => within(summary()).getByText('입금 완료 0명 / 2명'))
  })

  test('수령 지정도 입금과 서로 영향이 없다', async () => {
    seedCompleted({ paid2: true })
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1/check')
    await screen.findByRole('listitem', { name: '참여자' })
    await userEvent.click(within(row('참여자')).getByRole('button', { name: '수령 확인' }))
    await within(row('참여자')).findByText(/수령 확인됨/)
    within(row('참여자')).getByText(/입금 확인됨/)
    await waitFor(() => within(summary()).getByText('수령 완료 1명 / 2명'))
  })

  test('연타해도 한 번만 보내고 처리 중에는 버튼이 비활성이다', async () => {
    seedCompleted()
    mockState.sessionMemberId = 1
    let puts = 0
    server.use(
      http.put('/api/groupbuys/:id/participations/:memberId/payment', async ({ request }) => {
        puts++
        await new Promise((r) => setTimeout(r, 50))
        const { confirmed } = (await request.json()) as { confirmed: boolean }
        mockState.groupBuys[0]!.participations[1]!.paidConfirmedAt = confirmed ? PAID_AT : null // 재조회가 같은 상태를 보게
        return HttpResponse.json({
          memberId: 2, name: '참여자', quantity: 6, amount: 7200,
          paidConfirmedAt: confirmed ? PAID_AT : null, receivedConfirmedAt: null, receivedWithoutPayment: false,
        })
      }),
    )
    renderApp('/groupbuys/1/check')
    await screen.findByRole('listitem', { name: '참여자' })
    const btn = within(row('참여자')).getByRole('button', { name: '입금 확인' }) as HTMLButtonElement
    await userEvent.dblClick(btn)
    expect(btn.disabled).toBe(true)
    await within(row('참여자')).findByText(/입금 확인됨/)
    expect(puts).toBe(1)
  })

  test('입금 없이 수령된 행은 구분해 표시한다', async () => {
    seedCompleted({ received3: true })
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1/check')
    await screen.findByRole('listitem', { name: '구경꾼' })
    within(row('구경꾼')).getByText('입금 없이 수령됨')
    expect(within(row('참여자')).queryByText('입금 없이 수령됨')).toBeNull()
  })

  test('참여자가 없으면 빈 상태 문구', async () => {
    seedGroupBuy({ status: 'COMPLETED', participations: [{ memberId: 1, quantity: 24 }] })
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1/check')
    await screen.findByText('아직 참여자가 없어요.')
    within(summary()).getByText('입금 완료 0명 / 0명')
  })

  test('개설자가 아니면 안내와 상세로 가는 링크 (ORGANIZER_ONLY)', async () => {
    seedCompleted()
    mockState.sessionMemberId = 2
    renderApp('/groupbuys/1/check')
    await screen.findByText('개설자만 쓸 수 있어요.')
    expect(screen.getByRole('link', { name: '공구 상세로' }).getAttribute('href')).toBe('/groupbuys/1')
  })

  test('모집 완료가 아니면 안내와 상세로 가는 링크 (NOT_CHECKABLE)', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1/check')
    await screen.findByText('모집이 완료된 공구에서만 체크할 수 있어요.')
    screen.getByRole('link', { name: '공구 상세로' })
  })

  test('체크 중 NOT_CHECKABLE로 거부돼도 안내하고 상세로 보낸다', async () => {
    seedCompleted()
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1/check')
    await screen.findByRole('listitem', { name: '참여자' })
    server.use(
      http.put('/api/groupbuys/:id/participations/:memberId/payment', () =>
        HttpResponse.json({ title: 'NOT_CHECKABLE', status: 409 }, { status: 409 }),
      ),
    )
    await userEvent.click(within(row('참여자')).getByRole('button', { name: '입금 확인' }))
    await screen.findByText('모집이 완료된 공구에서만 체크할 수 있어요.')
    screen.getByRole('link', { name: '공구 상세로' })
  })

  test('체크는 알림을 보내지 않는다 (알림 요청 없음)', async () => {
    seedCompleted()
    mockState.sessionMemberId = 1
    const seen: string[] = []
    server.events.on('request:start', ({ request }) => void seen.push(new URL(request.url).pathname))
    renderApp('/groupbuys/1/check')
    await screen.findByRole('listitem', { name: '참여자' })
    await userEvent.click(within(row('참여자')).getByRole('button', { name: '입금 확인' }))
    await within(row('참여자')).findByText(/입금 확인됨/)
    expect(seen.filter((p) => p.includes('/notifications'))).toEqual([])
  })

  test('참여자 상세에 본인 입금·수령 확인 상태', async () => {
    seedCompleted({ paid2: true })
    mockState.sessionMemberId = 2
    renderApp('/groupbuys/1')
    await screen.findByText(/입금 확인됨/)
    screen.getByText(/수령 확인 전/)
  })
})
