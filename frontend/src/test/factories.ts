import type { SeedGroupBuy } from '../mocks/data/seed'
import { mockState } from '../mocks/state'

/** 테스트용 공구 하나를 목 서버에 넣는다. 개설자=1(개설자), 참여자=2, 비참여자=3. */
export function seedGroupBuy(over: Partial<SeedGroupBuy> = {}): SeedGroupBuy {
  const g: SeedGroupBuy = {
    id: 1,
    title: '햇반 24개 공구',
    description: '같이 사요',
    itemName: '햇반 210g',
    category: 'FOOD',
    itemUrl: 'https://example.com/item/1',
    unitPrice: 1200,
    totalQuantity: 24,
    deadline: '2099-11-01T18:00:00+09:00',
    status: 'OPEN',
    organizerId: 1,
    createdAt: '2026-10-25T07:30:00+09:00',
    openChatUrl: null,
    participations: [
      { memberId: 1, quantity: 3 },
      { memberId: 2, quantity: 6 },
    ],
    ...over,
  }
  mockState.groupBuys = [g]
  return g
}
