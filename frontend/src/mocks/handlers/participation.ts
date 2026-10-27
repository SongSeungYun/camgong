import { http, HttpResponse } from 'msw'
import type { SeedGroupBuy } from '../data/seed'
import { currentMember, mockState } from '../state'

const problem = (status: number, title: string, extra: Record<string, unknown> = {}) =>
  HttpResponse.json({ title, status, ...extra }, { status })

const secured = (g: SeedGroupBuy) => g.participations.reduce((n, p) => n + p.quantity, 0)
const state = (g: SeedGroupBuy, quantity: number) => ({
  groupBuyId: g.id,
  quantity,
  amount: g.unitPrice * quantity,
  securedQuantity: secured(g),
  remainingQuantity: g.totalQuantity - secured(g),
  status: g.status,
})

/** 004 계약 공통 사전 검사: 401 → (시나리오 강제 응답) → 404 → 403 */
function guard(id: string) {
  const me = currentMember()
  if (!me) return { res: problem(401, 'UNAUTHENTICATED') }
  const g = mockState.groupBuys.find((x) => x.id === Number(id))
  if (!g) return { res: problem(404, 'Not Found') }
  const forced = mockState.forceNext
  if (forced) {
    mockState.forceNext = null
    return { res: HttpResponse.json(forced.body, { status: forced.status }) }
  }
  if (g.organizerId === me.id) return { res: problem(403, 'ORGANIZER_NOT_ALLOWED') }
  return { g, me }
}

const closedReason = (g: SeedGroupBuy) =>
  g.status === 'COMPLETED' ? 'COMPLETED' : g.status === 'FAILED' ? 'FAILED' : g.status !== 'OPEN' || Date.parse(g.deadline) <= Date.now() ? 'DEADLINE_PASSED' : null

function readBody(b: { quantity?: unknown; expectedUnitPrice?: unknown }) {
  const errors: { field: string; message: string }[] = []
  if (!Number.isInteger(b.quantity) || (b.quantity as number) < 1) errors.push({ field: 'quantity', message: '수량은 1 이상의 정수여야 해요' })
  if (!Number.isInteger(b.expectedUnitPrice)) errors.push({ field: 'expectedUnitPrice', message: '확인한 개당 가격이 필요해요' })
  return errors
}

export const participationHandlers = [
  http.post('/api/groupbuys/:id/participations', async ({ params, request }) => {
    const { g, me, res } = guard(params.id as string)
    if (!g) return res
    const b = (await request.json()) as { quantity?: unknown; expectedUnitPrice?: unknown }
    const errors = readBody(b)
    if (errors.length) return problem(400, 'Bad Request', { errors })
    const quantity = b.quantity as number

    const mine = g.participations.find((p) => p.memberId === me.id)
    if (mine) {
      // 재시도 안전: 같은 수량이면 공구가 이후 COMPLETED여도 200. FAILED의 옛 참여는 거부.
      if (g.status === 'FAILED') return problem(409, 'NOT_JOINABLE', { reason: 'FAILED' })
      return mine.quantity === quantity ? HttpResponse.json(state(g, mine.quantity)) : problem(409, 'ALREADY_JOINED')
    }
    const reason = closedReason(g)
    if (reason) return problem(409, 'NOT_JOINABLE', { reason })
    if (mockState.priceChangeTo !== null) {
      g.unitPrice = mockState.priceChangeTo
      mockState.priceChangeTo = null
    }
    if (b.expectedUnitPrice !== g.unitPrice) return problem(409, 'PRICE_CHANGED', { currentUnitPrice: g.unitPrice })
    const remaining = g.totalQuantity - secured(g)
    if (quantity > remaining) return problem(409, 'INSUFFICIENT_QUANTITY', { remainingQuantity: remaining })

    g.participations.push({ memberId: me.id, quantity })
    if (secured(g) === g.totalQuantity) g.status = 'COMPLETED'
    return HttpResponse.json(state(g, quantity), { status: 201 })
  }),

  http.put('/api/groupbuys/:id/participations/me', async ({ params, request }) => {
    const { g, me, res } = guard(params.id as string)
    if (!g) return res
    const b = (await request.json()) as { quantity?: unknown; expectedUnitPrice?: unknown }
    const errors = readBody(b)
    if (errors.length) return problem(400, 'Bad Request', { errors })
    const quantity = b.quantity as number
    const mine = g.participations.find((p) => p.memberId === me.id)
    if (!mine) return problem(404, 'PARTICIPATION_NOT_FOUND')
    if (quantity === mine.quantity) return HttpResponse.json(state(g, quantity))
    const reason = closedReason(g)
    if (reason) return problem(409, 'NOT_JOINABLE', { reason })
    if (b.expectedUnitPrice !== g.unitPrice) return problem(409, 'PRICE_CHANGED', { currentUnitPrice: g.unitPrice })
    const remaining = g.totalQuantity - secured(g) + mine.quantity
    if (quantity > remaining) return problem(409, 'INSUFFICIENT_QUANTITY', { remainingQuantity: g.totalQuantity - secured(g) })
    mine.quantity = quantity
    if (secured(g) === g.totalQuantity) g.status = 'COMPLETED'
    return HttpResponse.json(state(g, quantity))
  }),

  http.delete('/api/groupbuys/:id/participations/me', ({ params }) => {
    const { g, me, res } = guard(params.id as string)
    if (!g) return res
    const mine = g.participations.find((p) => p.memberId === me.id)
    if (!mine) return problem(404, 'PARTICIPATION_NOT_FOUND')
    const reason = closedReason(g)
    if (reason) return problem(409, 'NOT_JOINABLE', { reason })
    g.participations = g.participations.filter((p) => p !== mine)
    return new HttpResponse(null, { status: 204 })
  }),
]
