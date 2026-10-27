import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { mockState } from '../../../mocks/state'
import { post } from '../../../shared/api/client'
import { makeGroupBuy } from '../../../test/factories'
import { renderApp } from '../../../test/utils'

// 내가(1) 연 공구 2개(id 1·2) + 남이 연 공구에 참여 3개(id 3·4·5). createdAt이 늦을수록 위.
const at = (n: number) => new Date(Date.UTC(2026, 9, 1, n)).toISOString()
function seedMine() {
  mockState.groupBuys = [
    makeGroupBuy({ id: 1, title: '내공구1', organizerId: 1, createdAt: at(1), participations: [{ memberId: 1, quantity: 2 }] }),
    makeGroupBuy({ id: 2, title: '내공구2', organizerId: 1, createdAt: at(2), status: 'COMPLETED', participations: [{ memberId: 1, quantity: 24 }] }),
    makeGroupBuy({ id: 3, title: '참여공구3', organizerId: 2, createdAt: at(3), participations: [{ memberId: 2, quantity: 3 }, { memberId: 1, quantity: 4 }] }),
    makeGroupBuy({ id: 4, title: '참여공구4', organizerId: 2, createdAt: at(4), status: 'PENDING_CLOSE', participations: [{ memberId: 2, quantity: 3 }, { memberId: 1, quantity: 5 }] }),
    makeGroupBuy({ id: 5, title: '참여공구5', organizerId: 3, createdAt: at(5), participations: [{ memberId: 3, quantity: 3 }, { memberId: 1, quantity: 6 }] }),
    makeGroupBuy({ id: 6, title: '남의공구', organizerId: 3, createdAt: at(6), participations: [{ memberId: 3, quantity: 3 }] }), // 나와 무관
  ]
  mockState.sessionMemberId = 1
}
const titles = () => screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)

describe('내 활동 (US5)', () => {
  test('28. 탭1은 내가 연 공구 2개, 탭2는 참여한 공구 5개(내가 연 것 포함), 최신 개설순 + 상태 뱃지', async () => {
    seedMine()
    renderApp('/me')
    await screen.findByRole('heading', { name: '내 활동' })
    await screen.findByText('내공구2')
    expect(titles()).toEqual(['내공구2', '내공구1'])
    screen.getByText('모집 완료')

    await userEvent.click(screen.getByRole('tab', { name: '참여한 공구' }))
    await screen.findByText('참여공구5')
    expect(titles()).toEqual(['참여공구5', '참여공구4', '참여공구3', '내공구2', '내공구1'])
    screen.getByText('마감됨(확정 대기)')
    screen.getByText('내 수량 6개') // 참여 탭은 내 수량을 보여준다
  })

  test('탭 선택은 주소에 남아 새로고침해도 유지된다', async () => {
    seedMine()
    const { router } = renderApp('/me')
    await userEvent.click(await screen.findByRole('tab', { name: '참여한 공구' }))
    expect(router.state.location.search).toBe('?tab=participant')
    expect(screen.getByRole('tab', { name: '참여한 공구' }).getAttribute('aria-selected')).toBe('true')
  })

  test('더 보기로 이어서 불러오고 끝에서 버튼이 사라진다', async () => {
    seedMine()
    mockState.groupBuys = Array.from({ length: 23 }, (_, i) =>
      makeGroupBuy({ id: i + 1, title: `공구${i + 1}`, organizerId: 1, createdAt: at(i + 1), participations: [{ memberId: 1, quantity: 1 }] }),
    )
    renderApp('/me')
    await screen.findByText('공구23')
    expect(titles()).toHaveLength(20)
    await userEvent.click(screen.getByRole('button', { name: '더 보기' }))
    await screen.findByText('공구1')
    expect(titles()).toHaveLength(23)
    expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull()
  })

  test('취소된 공구는 어느 탭에도 보이지 않는다', async () => {
    seedMine()
    await post('/api/groupbuys/1/cancel') // 개설자(1)가 취소
    renderApp('/me')
    await screen.findByText('내공구2')
    expect(screen.queryByText('내공구1')).toBeNull()
    await userEvent.click(screen.getByRole('tab', { name: '참여한 공구' }))
    await screen.findByText('참여공구5')
    expect(screen.queryByText('내공구1')).toBeNull()
  })

  test('항목을 누르면 상세로 간다', async () => {
    seedMine()
    const { router } = renderApp('/me')
    await userEvent.click(await screen.findByRole('link', { name: /내공구2/ }))
    await screen.findByRole('heading', { name: '내공구2', level: 1 })
    expect(router.state.location.pathname).toBe('/groupbuys/2')
  })

  test('빈 상태는 탭마다 다른 문구', async () => {
    mockState.groupBuys = []
    mockState.sessionMemberId = 1
    renderApp('/me')
    await screen.findByText('아직 연 공구가 없어요.')
    await userEvent.click(screen.getByRole('tab', { name: '참여한 공구' }))
    await screen.findByText('아직 참여한 공구가 없어요.')
  })

  test('비로그인은 로그인으로 보낸다', async () => {
    seedMine()
    mockState.sessionMemberId = null
    renderApp('/me')
    const main = await screen.findByRole('heading', { name: '로그인' })
    expect(within(main.closest('main')!).queryByText('내공구1')).toBeNull()
  })
})
