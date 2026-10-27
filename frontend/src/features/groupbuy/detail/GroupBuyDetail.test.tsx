import { screen, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { mockState } from '../../../mocks/state'
import type { GroupBuyDetail } from '../../../shared/api/types'
import { renderApp } from '../../../test/utils'
import { seedGroupBuy } from '../../../test/factories'
import { visibleActions } from './visibleActions'

const detail = (over: Partial<GroupBuyDetail> = {}): GroupBuyDetail => ({
  id: 1, title: 't', description: null, itemName: 'i', category: 'FOOD', itemUrl: null, unitPrice: 100,
  totalQuantity: 10, securedQuantity: 5, remainingQuantity: 5, deadline: '2099-01-01T00:00:00+09:00',
  status: 'OPEN', organizerName: 'o', isOrganizer: false, participantCount: 1, myParticipation: null,
  openChatUrl: null, participants: null, createdAt: '2026-01-01T00:00:00+09:00', ...over,
})
const mine = { quantity: 2, amount: 200 }

// data-model.md 3 '상세 영역 노출 규칙' 표를 한 행씩
describe('visibleActions', () => {
  test('수정·취소: 개설자이고 OPEN', () => {
    expect(visibleActions(detail({ isOrganizer: true })).edit).toBe(true)
    expect(visibleActions(detail({ isOrganizer: true, status: 'COMPLETED' })).edit).toBe(false)
    expect(visibleActions(detail({ isOrganizer: false })).edit).toBe(false)
  })
  test('참여: 개설자 아님 + 참여 전 + OPEN', () => {
    expect(visibleActions(detail()).join).toBe(true)
    expect(visibleActions(detail({ isOrganizer: true })).join).toBe(false)
    expect(visibleActions(detail({ myParticipation: mine })).join).toBe(false)
    expect(visibleActions(detail({ status: 'PENDING_CLOSE' })).join).toBe(false)
  })
  test('수량 변경·참여 취소: 참여했고 OPEN (개설자는 수정에서 바꾸므로 제외)', () => {
    expect(visibleActions(detail({ myParticipation: mine })).changeParticipation).toBe(true)
    expect(visibleActions(detail({ myParticipation: mine, status: 'COMPLETED' })).changeParticipation).toBe(false)
    expect(visibleActions(detail({ myParticipation: mine, isOrganizer: true })).changeParticipation).toBe(false)
  })
  test('오픈채팅 입장: openChatUrl이 있을 때만(화면이 조건을 재판단하지 않음)', () => {
    expect(visibleActions(detail({ openChatUrl: 'https://x.com/a' })).openChat).toBe(true)
    expect(visibleActions(detail({ status: 'COMPLETED' })).openChat).toBe(false)
  })
  test('참여자 명단: participants가 있을 때만', () => {
    expect(visibleActions(detail({ participants: [] })).participants).toBe(true)
    expect(visibleActions(detail()).participants).toBe(false)
  })
  test('체크표 이동: 개설자이고 COMPLETED', () => {
    expect(visibleActions(detail({ isOrganizer: true, status: 'COMPLETED' })).checkSheet).toBe(true)
    expect(visibleActions(detail({ isOrganizer: true })).checkSheet).toBe(false)
  })
  test('내 입금·수령 확인 상태: 참여했고 COMPLETED', () => {
    expect(visibleActions(detail({ myParticipation: mine, status: 'COMPLETED' })).myConfirm).toBe(true)
    expect(visibleActions(detail({ myParticipation: mine })).myConfirm).toBe(false)
  })
})

describe('상세 화면 (US2-5·6)', () => {
  test('개설자: 수정·취소 버튼, 참여자 명단, 참여 버튼 없음', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    screen.getByRole('link', { name: '수정' })
    screen.getByRole('button', { name: '공구 취소' })
    expect(screen.queryByRole('button', { name: '참여하기' })).toBeNull()
    const list = screen.getByRole('list', { name: '참여자' })
    within(list).getByText(/참여자/)
    screen.getByText('개설자', { selector: 'p span' }) // 개설자 이름
    screen.getByText(/참여 인원 2명/)
  })

  test('참여자: 내 참여 수량·금액과 수량 변경·참여 취소, 수정 없음', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 2
    renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    screen.getByText(/내 참여: 6개 · 7,200원/)
    screen.getByRole('button', { name: '수량 변경' })
    screen.getByRole('button', { name: '참여 취소' })
    expect(screen.queryByRole('link', { name: '수정' })).toBeNull()
    expect(screen.queryByRole('list', { name: '참여자' })).toBeNull() // 서버가 명단을 주지 않음
  })

  test('비참여자: 참여하기와 "참여 안 함"', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 3
    renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    screen.getByRole('button', { name: '참여하기' })
    screen.getByText('참여 안 함')
    screen.getByText(/총 24개/)
    screen.getByText(/확보 9개/)
    screen.getByText(/남은 15개/)
  })

  test('11. 오픈채팅·명단·체크표는 서버가 준 경우에만', async () => {
    seedGroupBuy({ status: 'COMPLETED', openChatUrl: 'https://open.kakao.com/o/abc' })
    mockState.sessionMemberId = 1
    const a = renderApp('/groupbuys/1')
    const chat = await screen.findByRole('link', { name: /오픈채팅 입장/ })
    expect(chat.getAttribute('href')).toBe('https://open.kakao.com/o/abc')
    expect(chat.getAttribute('target')).toBe('_blank')
    expect(chat.getAttribute('rel')).toBe('noopener noreferrer')
    screen.getByRole('link', { name: '체크표' })
    a.unmount()
    // 모집 중이면 참여자에게 서버가 오픈채팅을 주지 않는다 → 화면에도 없다
    seedGroupBuy({ openChatUrl: 'https://open.kakao.com/o/abc' })
    mockState.sessionMemberId = 2
    renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    expect(screen.queryByRole('link', { name: /오픈채팅/ })).toBeNull()
  })

  test('내 입금·수령 확인 상태(006 확장, 모집 완료)', async () => {
    seedGroupBuy({ status: 'COMPLETED', openChatUrl: 'https://open.kakao.com/o/abc' })
    mockState.sessionMemberId = 2
    renderApp('/groupbuys/1')
    await screen.findByText(/입금 확인 전/)
    screen.getByText(/수령 확인 전/)
  })

  test('6·12. 없는 공구는 찾을 수 없음 + 목록으로', async () => {
    seedGroupBuy()
    mockState.sessionMemberId = 1
    renderApp('/groupbuys/9999')
    await screen.findByText('찾을 수 없는 공구예요.')
    expect(screen.getByRole('link', { name: '목록으로' }).getAttribute('href')).toBe('/')
  })

  test('33. 구매처 링크는 https만 새 창으로, http는 링크로 만들지 않는다', async () => {
    seedGroupBuy({ itemUrl: 'https://example.com/item/1' })
    mockState.sessionMemberId = 3
    const a = renderApp('/groupbuys/1')
    const link = await screen.findByRole('link', { name: /구매처/ })
    expect(link.getAttribute('target')).toBe('_blank')
    expect(link.getAttribute('rel')).toBe('noopener noreferrer')
    expect(link.textContent).toContain('새 창')
    a.unmount()
    seedGroupBuy({ itemUrl: 'javascript:alert(1)' })
    renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '햇반 24개 공구' })
    expect(screen.queryByRole('link', { name: /구매처/ })).toBeNull()
  })

  test('34. 제목·설명의 HTML은 문자 그대로 보이고 실행되지 않는다', async () => {
    seedGroupBuy({ title: '<script>alert(1)</script><b>굵게</b>', description: '<img src=x onerror=alert(1)>' })
    mockState.sessionMemberId = 3
    const { container } = renderApp('/groupbuys/1')
    await screen.findByRole('heading', { name: '<script>alert(1)</script><b>굵게</b>' })
    screen.getByText('<img src=x onerror=alert(1)>')
    expect(container.querySelector('script, b, img')).toBeNull()
  })
})
