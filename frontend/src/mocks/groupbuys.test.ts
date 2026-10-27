import { expect, test } from 'vitest'
import { ApiError, get } from '../shared/api/client'
import type { GroupBuyCard, Page } from '../shared/api/types'

const list = (qs = '') => get<Page<GroupBuyCard>>(`/api/groupbuys${qs}`)
const rejects = async (p: Promise<unknown>) => {
  try {
    await p
  } catch (e) {
    return e as ApiError
  }
  throw new Error('거부될 것으로 기대했지만 성공했다')
}

test('기본은 모집 중 최신순, 카드 필드만 담긴다(003 목록 계약)', async () => {
  const r = await list()
  expect(r.items.length).toBe(20)
  expect(r.items.every((i) => i.status === 'OPEN')).toBe(true)
  expect(Object.keys(r.items[0]!).sort()).toEqual(
    ['category', 'deadline', 'id', 'itemName', 'securedQuantity', 'status', 'title', 'totalQuantity', 'unitPrice'],
  )
})

test('더 보기: 중복 없이 이어지고 끝에서 nextCursor는 null', async () => {
  const ids: number[] = []
  let cursor: string | null = null
  do {
    const r: Page<GroupBuyCard> = await list(`?size=7${cursor ? `&cursor=${cursor}` : ''}`)
    ids.push(...r.items.map((i) => i.id))
    cursor = r.nextCursor
  } while (cursor)
  expect(new Set(ids).size).toBe(ids.length)
  expect(ids.length).toBeGreaterThanOrEqual(25)
})

test('마감 임박순은 마감이 이른 순', async () => {
  const r = await list('?sort=deadline&size=50')
  const t = r.items.map((i) => Date.parse(i.deadline))
  expect(t).toEqual([...t].sort((a, b) => a - b))
})

test('카테고리·상태·검색(공백 단어 모두 포함) 필터', async () => {
  expect((await list('?category=FOOD&size=50')).items.every((i) => i.category === 'FOOD')).toBe(true)
  expect((await list('?status=FAILED&size=50')).items.every((i) => i.status === 'FAILED')).toBe(true)
  const hit = await list('?q=' + encodeURIComponent('햇반 공구') + '&size=50')
  expect(hit.items.length).toBeGreaterThan(0)
  expect(hit.items.every((i) => i.title.includes('햇반'))).toBe(true)
  expect((await list('?q=' + encodeURIComponent('없는물건'))).items).toEqual([])
})

test('400: 잘못된 status·category·sort·size·q, 조건이 다른 커서', async () => {
  for (const qs of ['?status=X', '?category=X', '?sort=X', '?size=0', '?size=51', `?q=${'a'.repeat(51)}`, '?cursor=garbage']) {
    expect(await rejects(list(qs))).toMatchObject({ status: 400, code: 'Bad Request' })
  }
  const { nextCursor } = await list()
  expect(await rejects(list(`?sort=deadline&cursor=${nextCursor}`))).toMatchObject({ status: 400 })
})
