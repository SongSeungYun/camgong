import { describe } from 'vitest'
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

// ---- 001 계약: 개설·수정·취소 ----
import { beforeEach } from 'vitest'
import { post, put } from '../shared/api/client'
import { mockState } from './state'

const body = (over: Record<string, unknown> = {}) => ({
  title: '햇반 24개', itemName: '햇반', unitPrice: 1200, totalQuantity: 24, organizerQuantity: 9,
  category: 'FOOD', itemUrl: 'https://example.com/a', openChatUrl: 'https://open.kakao.com/o/x',
  deadline: new Date(Date.now() + 2 * 86_400_000).toISOString(), ...over,
})
const fields = (e: ApiError) => e.fieldErrors.map((f) => f.field).sort()

describe('001 목 핸들러', () => {
  beforeEach(() => {
    mockState.sessionMemberId = 1
  })

  test('개설: 201 + 본문, 필드 오류는 한 번에 모두', async () => {
    const ok = await post<{ id: number; status: string; securedQuantity: number }>('/api/groupbuys', body())
    expect(ok).toMatchObject({ status: 'OPEN', securedQuantity: 9 })
    const e = await rejects(post('/api/groupbuys', body({ title: 'x'.repeat(101), unitPrice: 0, totalQuantity: 1, organizerQuantity: 0, itemUrl: 'http://a.com', openChatUrl: 'ftp://x', deadline: new Date(Date.now() - 1000).toISOString(), category: 'NOPE' })))
    expect(e).toMatchObject({ status: 400, code: 'Bad Request' })
    expect(fields(e)).toEqual(['category', 'deadline', 'itemUrl', 'openChatUrl', 'organizerQuantity', 'title', 'totalQuantity', 'unitPrice'])
  })

  test('개설: 내 수량 ≥ 총 수량, 마감 30일 초과는 400', async () => {
    expect(fields(await rejects(post('/api/groupbuys', body({ organizerQuantity: 24 }))))).toEqual(['organizerQuantity'])
    expect(fields(await rejects(post('/api/groupbuys', body({ deadline: new Date(Date.now() + 31 * 86_400_000).toISOString() }))))).toEqual(['deadline'])
  })

  test('개설: 같은 요청을 10초 안에 다시 보내면 409 DUPLICATE_REQUEST, 비로그인은 401', async () => {
    const same = body() // 마감 시각까지 같은 요청이어야 중복이다
    await post('/api/groupbuys', same)
    expect(await rejects(post('/api/groupbuys', same))).toMatchObject({ status: 409, code: 'DUPLICATE_REQUEST' })
    mockState.sessionMemberId = null
    expect(await rejects(post('/api/groupbuys', body({ title: '다른 공구' })))).toMatchObject({ status: 401 })
  })

  const open = () => mockState.groupBuys.find((g) => g.status === 'OPEN' && g.organizerId === 1 && g.participations.length > 1)!

  test('수정: 마감 단축·총 수량 < 확보는 400, 비개설자 403, 마감 지나면 409 NOT_MODIFIABLE', async () => {
    const g = open()
    const ok = body({ deadline: g.deadline, totalQuantity: 60 })
    expect(await put(`/api/groupbuys/${g.id}`, ok)).toMatchObject({ id: g.id, totalQuantity: 60 })
    expect(fields(await rejects(put(`/api/groupbuys/${g.id}`, body({ deadline: new Date(Date.parse(g.deadline) - 60_000).toISOString(), totalQuantity: 60 }))))).toEqual(['deadline'])
    expect(fields(await rejects(put(`/api/groupbuys/${g.id}`, body({ deadline: g.deadline, totalQuantity: 2, organizerQuantity: 1 }))))).toEqual(['totalQuantity'])
    mockState.sessionMemberId = 3
    expect(await rejects(put(`/api/groupbuys/${g.id}`, ok))).toMatchObject({ status: 403, code: 'ORGANIZER_ONLY' })
    mockState.sessionMemberId = 1
    g.deadline = new Date(Date.now() - 1000).toISOString()
    expect(await rejects(put(`/api/groupbuys/${g.id}`, ok))).toMatchObject({ status: 409, code: 'NOT_MODIFIABLE', reason: 'DEADLINE_PASSED' })
  })

  test('수정/취소: 모집 완료 공구는 409 reason=COMPLETED, 없는 공구는 404', async () => {
    const done = mockState.groupBuys.find((g) => g.status === 'COMPLETED')!
    mockState.sessionMemberId = done.organizerId
    expect(await rejects(post(`/api/groupbuys/${done.id}/cancel`))).toMatchObject({ status: 409, reason: 'COMPLETED' })
    const failed = mockState.groupBuys.find((g) => g.status === 'FAILED')!
    mockState.sessionMemberId = failed.organizerId
    expect(await rejects(post(`/api/groupbuys/${failed.id}/cancel`))).toMatchObject({ status: 409, reason: 'FAILED' }) // 001 계약(2026-10-28)
    mockState.sessionMemberId = done.organizerId
    expect(await rejects(post('/api/groupbuys/9999/cancel'))).toMatchObject({ status: 404 })
  })

  test('취소: 204 후 상세는 404', async () => {
    const g = open()
    expect(await post(`/api/groupbuys/${g.id}/cancel`)).toBeUndefined()
    expect(await rejects(get(`/api/groupbuys/${g.id}`))).toMatchObject({ status: 404 })
  })

  test('상세 오픈채팅 공개 규칙: 개설자는 모집 중에도, 참여자는 모집 완료 때만', async () => {
    const g = open()
    const other = g.participations.find((p) => p.memberId !== g.organizerId)?.memberId
    expect((await get<{ openChatUrl: string | null }>(`/api/groupbuys/${g.id}`)).openChatUrl).toBe(g.openChatUrl)
    if (other) {
      mockState.sessionMemberId = other
      expect((await get<{ openChatUrl: string | null }>(`/api/groupbuys/${g.id}`)).openChatUrl).toBeNull()
    }
  })
})

test('내 활동 목: role 누락·잘못된 값은 400, 비로그인은 401', async () => {
  mockState.sessionMemberId = 1
  expect(await rejects(get('/api/me/groupbuys'))).toMatchObject({ status: 400, code: 'Bad Request' })
  expect(await rejects(get('/api/me/groupbuys?role=x'))).toMatchObject({ status: 400 })
  expect(await rejects(get('/api/me/groupbuys?role=organizer&size=0'))).toMatchObject({ status: 400 })
  mockState.sessionMemberId = null
  expect(await rejects(get('/api/me/groupbuys?role=organizer'))).toMatchObject({ status: 401 })
})
