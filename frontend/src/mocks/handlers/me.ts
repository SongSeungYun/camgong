import { http, HttpResponse } from 'msw'
import { currentMember, mockState } from '../state'
import { decodeCursor, encodeCursor, toCard } from './groupbuys'

const problem = (status: number, title: string, extra: Record<string, unknown> = {}) =>
  HttpResponse.json({ title, status, ...extra }, { status })
const badRequest = (field: string, message: string) => problem(400, 'Bad Request', { errors: [{ field, message }] })

export const meHandlers = [
  // 003 내 활동: organizer=내가 연 공구, participant=내가 참여한 공구(내가 연 공구 포함, myQuantity 추가). 개설 시각 늦은 순, 취소 제외.
  http.get('/api/me/groupbuys', ({ request }) => {
    const me = currentMember()
    if (!me) return problem(401, 'UNAUTHENTICATED')
    const sp = new URL(request.url).searchParams
    const role = sp.get('role')
    const sizeRaw = sp.get('size')
    const size = sizeRaw === null ? 20 : Number(sizeRaw)
    if (role !== 'organizer' && role !== 'participant') return badRequest('role', 'organizer 또는 participant여야 해요')
    if (!Number.isInteger(size) || size < 1 || size > 50) return badRequest('size', '1~50 사이여야 해요')

    const rows = mockState.groupBuys
      .filter((g) => (role === 'organizer' ? g.organizerId === me.id : g.participations.some((p) => p.memberId === me.id)))
      .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt) || b.id - a.id)

    const sig = `me|${me.id}|${role}`
    const cursor = sp.get('cursor')
    const offset = cursor === null ? 0 : decodeCursor(cursor, sig)
    if (offset === null) return badRequest('cursor', '올바르지 않은 커서예요')

    const items = rows.slice(offset, offset + size).map((g) => ({
      ...toCard(g),
      ...(role === 'participant' ? { myQuantity: g.participations.find((p) => p.memberId === me.id)!.quantity } : {}),
    }))
    return HttpResponse.json({ items, nextCursor: offset + size < rows.length ? encodeCursor(sig, offset + size) : null })
  }),
]
