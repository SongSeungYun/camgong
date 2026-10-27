import { http, HttpResponse } from 'msw'
import { isKnownCategory, isKnownStatus } from '../../shared/api/types'
import type { SeedGroupBuy } from '../data/seed'
import { currentMember, mockState } from '../state'

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

  // 003 상세: 로그인 필수. 역할별로 myParticipation·openChatUrl·participants가 달라진다.
  http.get('/api/groupbuys/:id', ({ params }) => {
    const me = currentMember()
    if (!me) return problem(401, 'UNAUTHENTICATED')
    const g = mockState.groupBuys.find((x) => x.id === Number(params.id))
    if (!g) return problem(404, 'Not Found')
    const mine = g.participations.find((p) => p.memberId === me.id)
    const isOrganizer = g.organizerId === me.id
    const total = secured(g)
    const nameOf = (id: number) => mockState.members.find((m) => m.id === id)?.displayName ?? '알 수 없음'
    return HttpResponse.json({
      id: g.id,
      title: g.title,
      description: g.description,
      itemName: g.itemName,
      category: g.category,
      itemUrl: g.itemUrl,
      unitPrice: g.unitPrice,
      totalQuantity: g.totalQuantity,
      securedQuantity: total,
      remainingQuantity: g.totalQuantity - total,
      deadline: g.deadline,
      status: g.status,
      organizerName: nameOf(g.organizerId),
      isOrganizer,
      participantCount: g.participations.length,
      myParticipation: mine
        ? {
            quantity: mine.quantity,
            amount: g.unitPrice * mine.quantity,
            // 006 확장: 개설자의 myParticipation에는 확인 필드가 없다
            ...(isOrganizer ? {} : { paidConfirmedAt: null, receivedConfirmedAt: null }),
          }
        : null,
      // 공개 규칙: 모집 완료일 때 개설자·참여자에게만 오픈채팅 링크를 준다
      openChatUrl: g.status === 'COMPLETED' && (isOrganizer || mine) ? g.openChatUrl : null,
      participants: isOrganizer ? g.participations.map((p) => ({ memberId: p.memberId, name: nameOf(p.memberId), quantity: p.quantity })) : null,
      createdAt: g.createdAt,
    })
  }),
]
