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
export const encodeCursor = (sig: string, offset: number) => btoa(JSON.stringify({ sig, offset }))
export const decodeCursor = (c: string, sig: string): number | null => {
  try {
    const v = JSON.parse(atob(c)) as { sig: string; offset: number }
    return v.sig === sig && Number.isInteger(v.offset) && v.offset >= 0 ? v.offset : null
  } catch {
    return null
  }
}


const DAY = 86_400_000
const isHttps = (v: unknown) => typeof v === 'string' && v.length <= 500 && /^https:\/\/[^\s]+$/.test(v)
const isInt = (v: unknown, min: number, max = Infinity): v is number => Number.isInteger(v) && (v as number) >= min && (v as number) <= max

interface Body {
  title?: unknown; description?: unknown; itemName?: unknown; unitPrice?: unknown; totalQuantity?: unknown
  deadline?: unknown; organizerQuantity?: unknown; category?: unknown; itemUrl?: unknown; openChatUrl?: unknown
}

/** 001 계약 '공통 요청 본문' 검증. 개설과 수정의 차이는 마감·수량 규칙. */
function validate(b: Body, ctx: { existing?: SeedGroupBuy; others?: number }): { field: string; message: string }[] {
  const errors: { field: string; message: string }[] = []
  const add = (field: string, message: string) => errors.push({ field, message })
  const title = typeof b.title === 'string' ? b.title.trim() : ''
  if (!title) add('title', '제목을 입력해 주세요')
  else if (title.length > 100) add('title', '제목은 100자 이하여야 해요')
  if (b.description != null && (typeof b.description !== 'string' || b.description.length > 2000)) add('description', '설명은 2000자 이하여야 해요')
  const itemName = typeof b.itemName === 'string' ? b.itemName.trim() : ''
  if (!itemName) add('itemName', '상품명을 입력해 주세요')
  else if (itemName.length > 100) add('itemName', '상품명은 100자 이하여야 해요')
  if (!isInt(b.unitPrice, 1, 1_000_000)) add('unitPrice', '개당 가격은 1~1,000,000원이어야 해요')
  if (!isInt(b.totalQuantity, 2, 1000)) add('totalQuantity', '총 수량은 2~1,000개여야 해요')
  if (typeof b.category !== 'string' || !isKnownCategory(b.category)) add('category', '카테고리를 선택해 주세요')
  if (!isHttps(b.itemUrl)) add('itemUrl', '구매처 링크는 https 주소여야 해요')
  if (!isHttps(b.openChatUrl)) add('openChatUrl', '오픈채팅 링크는 https 주소여야 해요')

  const deadline = typeof b.deadline === 'string' ? Date.parse(b.deadline) : NaN
  const base = ctx.existing ? Date.parse(ctx.existing.createdAt) : Date.now()
  if (Number.isNaN(deadline)) add('deadline', '마감 시각을 입력해 주세요')
  else if (deadline > base + 30 * DAY) add('deadline', '마감은 개설 시점부터 30일 이내여야 해요')
  else if (!ctx.existing && deadline <= Date.now()) add('deadline', '마감 시각은 현재보다 미래여야 해요')
  else if (ctx.existing && deadline < Date.parse(ctx.existing.deadline)) add('deadline', '마감은 늘리기만 할 수 있어요')

  if (!isInt(b.organizerQuantity, 1)) add('organizerQuantity', '내 수량은 1 이상이어야 해요')
  else if (isInt(b.totalQuantity, 2, 1000)) {
    if (!ctx.existing && b.organizerQuantity >= b.totalQuantity) add('organizerQuantity', '내 수량은 총 수량보다 적어야 해요')
    if (ctx.existing && (ctx.others ?? 0) + b.organizerQuantity > b.totalQuantity) add('totalQuantity', '총 수량은 확보 수량 이상이어야 해요')
  }
  return errors
}

const body001 = (g: SeedGroupBuy) => ({
  id: g.id, title: g.title, itemName: g.itemName, unitPrice: g.unitPrice, totalQuantity: g.totalQuantity,
  securedQuantity: secured(g), remainingQuantity: g.totalQuantity - secured(g), deadline: g.deadline, status: g.status,
  organizerId: g.organizerId, organizerQuantity: g.participations.find((p) => p.memberId === g.organizerId)?.quantity ?? 0,
  category: g.category, itemUrl: g.itemUrl, openChatUrl: g.openChatUrl, description: g.description, createdAt: g.createdAt,
})

function applyBody(g: SeedGroupBuy, b: Body) {
  g.title = (b.title as string).trim()
  g.description = typeof b.description === 'string' && b.description ? b.description : null
  g.itemName = (b.itemName as string).trim()
  g.category = b.category as string
  g.itemUrl = b.itemUrl as string
  g.openChatUrl = b.openChatUrl as string
  g.unitPrice = b.unitPrice as number
  g.totalQuantity = b.totalQuantity as number
  g.deadline = new Date(Date.parse(b.deadline as string)).toISOString()
  const mine = g.participations.find((p) => p.memberId === g.organizerId)
  if (mine) mine.quantity = b.organizerQuantity as number
  else g.participations.push({ memberId: g.organizerId, quantity: b.organizerQuantity as number })
  if (secured(g) === g.totalQuantity) g.status = 'COMPLETED'
}

/** 수정·취소 공통 사전 검사: 401 → 404 → 403 → 409 */
function guardOrganizer(id: string) {
  const me = currentMember()
  if (!me) return { res: problem(401, 'UNAUTHENTICATED') }
  const g = mockState.groupBuys.find((x) => x.id === Number(id))
  if (!g) return { res: problem(404, 'Not Found') }
  if (g.organizerId !== me.id) return { res: problem(403, 'ORGANIZER_ONLY') }
  if (g.status !== 'OPEN' || Date.parse(g.deadline) <= Date.now()) {
    return { res: problem(409, 'NOT_MODIFIABLE', { reason: g.status === 'COMPLETED' || g.status === 'FAILED' ? g.status : 'DEADLINE_PASSED' }) }
  }
  return { g }
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
            ...(isOrganizer ? {} : { paidConfirmedAt: mine.paidConfirmedAt ?? null, receivedConfirmedAt: mine.receivedConfirmedAt ?? null }),
          }
        : null,
      // 공개 규칙(003 data-model): 개설자는 OPEN·PENDING_CLOSE·COMPLETED에서 항상, 참여자는 COMPLETED일 때만
      openChatUrl: (isOrganizer && ['OPEN', 'PENDING_CLOSE', 'COMPLETED'].includes(g.status)) || (mine && g.status === 'COMPLETED') ? g.openChatUrl : null,
      participants: isOrganizer ? g.participations.map((p) => ({ memberId: p.memberId, name: nameOf(p.memberId), quantity: p.quantity })) : null,
      createdAt: g.createdAt,
    })
  }),

  http.post('/api/groupbuys', async ({ request }) => {
    const me = currentMember()
    if (!me) return problem(401, 'UNAUTHENTICATED')
    const b = (await request.json()) as Body
    const key = `${me.id}:${JSON.stringify(b)}`
    const now = Date.now()
    mockState.recentCreates = mockState.recentCreates.filter((r) => now - r.at < 10_000)
    if (mockState.recentCreates.some((r) => r.key === key)) return problem(409, 'DUPLICATE_REQUEST')
    const errors = validate(b, {})
    if (errors.length) return problem(400, 'Bad Request', { errors })
    mockState.recentCreates.push({ key, at: now })
    const g = {
      id: Math.max(0, ...mockState.groupBuys.map((x) => x.id)) + 1,
      status: 'OPEN', organizerId: me.id, createdAt: new Date(now).toISOString(), participations: [],
    } as unknown as SeedGroupBuy
    applyBody(g, b)
    mockState.groupBuys.unshift(g)
    return HttpResponse.json(body001(g), { status: 201, headers: { Location: `/api/groupbuys/${g.id}` } })
  }),

  http.put('/api/groupbuys/:id', async ({ params, request }) => {
    const { g, res } = guardOrganizer(params.id as string)
    if (!g) return res
    const b = (await request.json()) as Body
    const others = g.participations.filter((p) => p.memberId !== g.organizerId).reduce((n, p) => n + p.quantity, 0)
    const errors = validate(b, { existing: g, others })
    if (errors.length) return problem(400, 'Bad Request', { errors })
    applyBody(g, b)
    return HttpResponse.json(body001(g))
  }),

  http.post('/api/groupbuys/:id/cancel', ({ params }) => {
    const { g, res } = guardOrganizer(params.id as string)
    if (!g) return res
    mockState.groupBuys = mockState.groupBuys.filter((x) => x !== g)
    return new HttpResponse(null, { status: 204 })
  }),
]
