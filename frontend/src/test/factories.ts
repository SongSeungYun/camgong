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
    deadline: new Date(Math.floor((Date.now() + 7 * 86_400_000) / 60_000) * 60_000).toISOString(), // 분 단위(폼 입력 정밀도) // 개설 시점부터 30일 이내
    status: 'OPEN',
    organizerId: 1,
    createdAt: new Date().toISOString(),
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
