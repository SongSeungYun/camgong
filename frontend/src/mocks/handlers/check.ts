import { http, HttpResponse } from 'msw'
import type { SeedGroupBuy } from '../data/seed'
import { currentMember, mockState } from '../state'

const problem = (status: number, title: string, extra: Record<string, unknown> = {}) =>
  HttpResponse.json({ title, status, ...extra }, { status })

type P = SeedGroupBuy['participations'][number]
const nameOf = (id: number) => mockState.members.find((m) => m.id === id)?.displayName ?? '알 수 없음'
const row = (g: SeedGroupBuy, p: P) => ({
  memberId: p.memberId,
  name: nameOf(p.memberId),
  quantity: p.quantity,
  amount: g.unitPrice * p.quantity,
  paidConfirmedAt: p.paidConfirmedAt ?? null,
  receivedConfirmedAt: p.receivedConfirmedAt ?? null,
  receivedWithoutPayment: !p.paidConfirmedAt && !!p.receivedConfirmedAt,
})

/** 006 계약 공통 검사: 401 → 404(없음·취소) → 403 → 409. 개설자 제외 참여자만 다룬다. */
function guard(id: string) {
  const me = currentMember()
  if (!me) return { res: problem(401, 'UNAUTHENTICATED') }
  const g = mockState.groupBuys.find((x) => x.id === Number(id))
  if (!g) return { res: problem(404, 'Not Found') }
  if (g.organizerId !== me.id) return { res: problem(403, 'ORGANIZER_ONLY') }
  if (g.status !== 'COMPLETED') return { res: problem(409, 'NOT_CHECKABLE') }
  return { g }
}

function setConfirmed(field: 'paidConfirmedAt' | 'receivedConfirmedAt') {
  return async ({ params, request }: { params: Record<string, unknown>; request: Request }) => {
    const { g, res } = guard(params.id as string)
    if (!g) return res
    const b = (await request.json().catch(() => ({}))) as { confirmed?: unknown }
    const memberId = Number(params.memberId)
    if (typeof b.confirmed !== 'boolean') return problem(400, 'Bad Request', { errors: [{ field: 'confirmed', message: 'true 또는 false여야 해요' }] })
    if (memberId === g.organizerId) return problem(400, 'Bad Request', { errors: [{ field: 'memberId', message: '개설자는 체크 대상이 아니에요' }] })
    const p = g.participations.find((x) => x.memberId === memberId)
    if (!p) return problem(404, 'Not Found')
    // 멱등: 이미 확인된 것을 다시 true로 지정해도 시각을 바꾸지 않는다
    if (b.confirmed) p[field] ??= new Date().toISOString()
    else p[field] = null
    return HttpResponse.json(row(g, p))
  }
}

export const checkHandlers = [
  http.get('/api/groupbuys/:id/check-sheet', ({ params, request }) => {
    const { g, res } = guard(params.id as string)
    if (!g) return res
    const filter = new URL(request.url).searchParams.get('filter') ?? 'all'
    if (!['all', 'unpaid', 'unreceived'].includes(filter)) return problem(400, 'Bad Request', { errors: [{ field: 'filter', message: '지원하지 않는 필터예요' }] })
    const all = g.participations.filter((p) => p.memberId !== g.organizerId)
    const rows = all.filter((p) => (filter === 'unpaid' ? !p.paidConfirmedAt : filter === 'unreceived' ? !p.receivedConfirmedAt : true))
    const amount = (p: P) => g.unitPrice * p.quantity
    return HttpResponse.json({
      summary: {
        participantCount: all.length,
        paidCount: all.filter((p) => p.paidConfirmedAt).length,
        receivedCount: all.filter((p) => p.receivedConfirmedAt).length,
        amountDue: all.reduce((n, p) => n + amount(p), 0),
        amountConfirmed: all.filter((p) => p.paidConfirmedAt).reduce((n, p) => n + amount(p), 0),
      },
      rows: rows.map((p) => row(g, p)),
    })
  }),
  http.put('/api/groupbuys/:id/participations/:memberId/payment', setConfirmed('paidConfirmedAt')),
  http.put('/api/groupbuys/:id/participations/:memberId/receipt', setConfirmed('receivedConfirmedAt')),
]
