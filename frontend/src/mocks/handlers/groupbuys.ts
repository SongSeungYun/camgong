import { http, HttpResponse } from 'msw'
import { isKnownCategory, isKnownStatus } from '../../shared/api/types'
import type { SeedGroupBuy } from '../data/seed'
import { mockState } from '../state'

const problem = (status: number, title: string, extra: Record<string, unknown> = {}) =>
  HttpResponse.json({ title, status, ...extra }, { status })
const badRequest = (field: string, message: string) => problem(400, 'Bad Request', { errors: [{ field, message }] })

const secured = (g: SeedGroupBuy) => g.participations.reduce((n, p) => n + p.quantity, 0)

/** 003 목록 카드 모양. 개설자·구매처는 넣지 않는다. */
export const toCard = (g: SeedGroupBuy) => ({
  id: g.id,
  title: g.title,
  itemName: g.itemName,
  category: g.category,
  unitPrice: g.unitPrice,
  securedQuantity: secured(g),
  totalQuantity: g.totalQuantity,
  deadline: g.deadline,
  status: g.status,
})

// 불투명 커서: 조건 서명 + 오프셋. 조건이 다르면 거부한다(003: sort·필터와 맞지 않으면 400).
const encodeCursor = (sig: string, offset: number) => btoa(JSON.stringify({ sig, offset }))
const decodeCursor = (c: string, sig: string): number | null => {
  try {
    const v = JSON.parse(atob(c)) as { sig: string; offset: number }
    return v.sig === sig && Number.isInteger(v.offset) && v.offset >= 0 ? v.offset : null
  } catch {
    return null
  }
}

export const groupBuyHandlers = [
  http.get('/api/groupbuys', ({ request }) => {
    const sp = new URL(request.url).searchParams
    const status = sp.get('status') ?? 'OPEN'
    const category = sp.get('category')
    const sort = sp.get('sort') ?? 'latest'
    const q = (sp.get('q') ?? '').trim()
    const sizeRaw = sp.get('size')
    const size = sizeRaw === null ? 20 : Number(sizeRaw)

    if (!isKnownStatus(status)) return badRequest('status', '지원하지 않는 상태예요')
    if (category !== null && !isKnownCategory(category)) return badRequest('category', '지원하지 않는 카테고리예요')
    if (sort !== 'latest' && sort !== 'deadline') return badRequest('sort', '지원하지 않는 정렬이에요')
    if (!Number.isInteger(size) || size < 1 || size > 50) return badRequest('size', '1~50 사이여야 해요')
    if (q.length > 50) return badRequest('q', '50자 이하여야 해요')

    const words = q.split(/\s+/).filter(Boolean).slice(0, 5).map((w) => w.toLowerCase())
    const rows = mockState.groupBuys
      .filter((g) => g.status === status)
      .filter((g) => !category || g.category === category)
      .filter((g) => words.every((w) => g.title.toLowerCase().includes(w) || g.itemName.toLowerCase().includes(w)))
      .sort((a, b) =>
        sort === 'deadline'
          ? Date.parse(a.deadline) - Date.parse(b.deadline) || a.id - b.id
          : Date.parse(b.createdAt) - Date.parse(a.createdAt) || b.id - a.id,
      )

    const sig = [status, category, sort, words.join(' ')].join('|')
    const cursor = sp.get('cursor')
    const offset = cursor === null ? 0 : decodeCursor(cursor, sig)
    if (offset === null) return badRequest('cursor', '올바르지 않은 커서예요')

    const slice = rows.slice(offset, offset + size)
    const next = offset + size < rows.length ? encodeCursor(sig, offset + size) : null
    return HttpResponse.json({ items: slice.map(toCard), nextCursor: next })
  }),
]
