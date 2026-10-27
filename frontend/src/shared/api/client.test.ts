import { http, HttpResponse } from 'msw'
import { afterEach, expect, test, vi } from 'vitest'
import { server } from '../../mocks/server'
import { ApiError, get, post, setUnauthenticatedHandler } from './client'

const fail = (p: Promise<unknown>) => p.catch((e: unknown) => e as ApiError)

afterEach(() => setUnauthenticatedHandler(() => {}))

test('쿠키를 포함하고 X-Member-Id·CSRF 헤더 없이 JSON을 보낸다', async () => {
  let seen: Request | undefined
  server.use(
    http.post('/api/x', ({ request }) => {
      seen = request
      return HttpResponse.json({ ok: true })
    }),
  )
  const spy = vi.spyOn(globalThis, 'fetch')
  await post('/api/x', { a: 1 })
  // MSW가 요청을 다시 만들어 credentials가 사라지므로 fetch 호출 인자로 확인한다.
  expect((spy.mock.calls[0]![1] as RequestInit).credentials).toBe('include')
  spy.mockRestore()
  expect(seen!.headers.get('content-type')).toBe('application/json')
  expect(seen!.headers.get('x-member-id')).toBeNull()
  expect([...seen!.headers.keys()].filter((k) => k.includes('csrf') || k.includes('xsrf'))).toEqual([])
})

test('ProblemDetail을 ApiError로 바꾼다(code·reason·fieldErrors·추가 속성)', async () => {
  server.use(
    http.get('/api/x', () =>
      HttpResponse.json(
        { title: 'PRICE_CHANGED', status: 409, detail: '내부 설명', currentUnitPrice: 1300, errors: [{ field: 'q', message: '길어요' }], reason: 'R' },
        { status: 409 },
      ),
    ),
  )
  const err = (await fail(get('/api/x'))) as ApiError
  expect(err).toBeInstanceOf(ApiError)
  expect(err).toMatchObject({ status: 409, code: 'PRICE_CHANGED', reason: 'R', fieldErrors: [{ field: 'q', message: '길어요' }], extra: { currentUnitPrice: 1300 } })
  expect(JSON.stringify(err.extra)).not.toContain('내부 설명')
})

test('5xx는 일반 오류이고 서버 detail을 노출하지 않는다', async () => {
  server.use(http.get('/api/x', () => HttpResponse.json({ title: 'X', detail: 'stack trace' }, { status: 500 })))
  const err = (await fail(get('/api/x'))) as ApiError
  expect(err).toMatchObject({ status: 500, code: undefined, fieldErrors: [], extra: {} })
  expect(err.message).not.toContain('stack')
})

test('네트워크 오류는 status 0', async () => {
  server.use(http.get('/api/x', () => HttpResponse.error()))
  expect(await fail(get('/api/x'))).toMatchObject({ status: 0 })
})

test('401이면 onUnauthenticated를 부르지만 INVALID_CREDENTIALS는 부르지 않는다', async () => {
  const cb = vi.fn()
  setUnauthenticatedHandler(cb)
  server.use(
    http.get('/api/a', () => HttpResponse.json({ title: 'UNAUTHENTICATED' }, { status: 401 })),
    http.post('/api/b', () => HttpResponse.json({ title: 'INVALID_CREDENTIALS' }, { status: 401 })),
  )
  await get('/api/a').catch(() => {})
  expect(cb).toHaveBeenCalledTimes(1)
  await post('/api/b').catch(() => {})
  expect(cb).toHaveBeenCalledTimes(1)
})

test('204는 undefined', async () => {
  server.use(http.post('/api/x', () => new HttpResponse(null, { status: 204 })))
  expect(await post('/api/x')).toBeUndefined()
})
