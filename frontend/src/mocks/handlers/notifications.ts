import { http, HttpResponse } from 'msw'
import { currentMember, mockState, type MockNotification } from '../state'
import { decodeCursor, encodeCursor } from './groupbuys'

const problem = (status: number, title: string, extra: Record<string, unknown> = {}) =>
  HttpResponse.json({ title, status, ...extra }, { status })

// 수신자(memberId)는 응답에 내려가지 않는다(007: 다른 회원 정보 없음)
const item = ({ memberId: _m, ...n }: MockNotification) => n
const mine = (memberId: number) => mockState.notifications.filter((n) => n.memberId === memberId).sort((a, b) => b.id - a.id)

export const notificationHandlers = [
  http.get('/api/notifications', ({ request }) => {
    const me = currentMember()
    if (!me) return problem(401, 'UNAUTHENTICATED')
    const sp = new URL(request.url).searchParams
    const size = sp.get('size') === null ? 20 : Number(sp.get('size'))
    if (!Number.isInteger(size) || size < 1 || size > 50) return problem(400, 'Bad Request', { errors: [{ field: 'size', message: '1~50 사이여야 해요' }] })
    const sig = `notifications|${me.id}`
    const cursor = sp.get('cursor')
    const offset = cursor === null ? 0 : decodeCursor(cursor, sig)
    if (offset === null) return problem(400, 'Bad Request', { errors: [{ field: 'cursor', message: '올바르지 않은 커서예요' }] })
    const rows = mine(me.id)
    return HttpResponse.json({
      items: rows.slice(offset, offset + size).map(item),
      nextCursor: offset + size < rows.length ? encodeCursor(sig, offset + size) : null,
    })
  }),

  http.get('/api/notifications/unread-count', () => {
    const me = currentMember()
    if (!me) return problem(401, 'UNAUTHENTICATED')
    return HttpResponse.json({ unreadCount: mine(me.id).filter((n) => !n.read).length })
  }),

  // 멱등. 없는 알림과 남의 알림은 같은 404(소유 여부를 알리지 않는다)
  http.put('/api/notifications/:id/read', ({ params }) => {
    const me = currentMember()
    if (!me) return problem(401, 'UNAUTHENTICATED')
    const n = mockState.notifications.find((x) => x.id === Number(params.id) && x.memberId === me.id)
    if (!n) return problem(404, 'Not Found')
    n.read = true
    return HttpResponse.json(item(n))
  }),
]
