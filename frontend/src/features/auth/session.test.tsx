import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { get } from '../../shared/api/client'
import { server } from '../../mocks/server'
import { mockState } from '../../mocks/state'
import { renderApp } from '../../test/utils'

test('시작 시 me로 로그인 상태를 정한다(authenticated)', async () => {
  mockState.sessionMemberId = 1
  renderApp('/')
  await screen.findByRole('button', { name: '로그아웃' })
})

test('me가 401이면 anonymous(로그인·가입 링크)', async () => {
  renderApp('/')
  await screen.findByRole('link', { name: '로그인' })
  expect(screen.queryByRole('button', { name: '로그아웃' })).toBeNull()
})

test('요청이 401을 받으면 세션을 비우고 쿼리 캐시를 전부 비운다', async () => {
  mockState.sessionMemberId = 1
  const { client } = renderApp('/')
  await screen.findByRole('button', { name: '로그아웃' })
  client.setQueryData(['groupbuy', 1], { secret: true })
  mockState.sessionExpired = true
  await get('/api/auth/me').catch(() => {})
  await waitFor(() => screen.getByRole('link', { name: '로그인' }))
  expect(client.getQueryData(['groupbuy', 1])).toBeUndefined()
})

test('로그아웃하면 anonymous가 되고 캐시를 비운다', async () => {
  mockState.sessionMemberId = 1
  const { client } = renderApp('/')
  client.setQueryData(['groupbuys'], { x: 1 })
  await userEvent.click(await screen.findByRole('button', { name: '로그아웃' }))
  await screen.findByRole('link', { name: '로그인' })
  expect(client.getQueryData(['groupbuys'])).toBeUndefined()
  expect(mockState.sessionMemberId).toBeNull()
})

test('시작 시 me가 401이어도 공개 화면의 쿼리 캐시를 비우지 않는다', async () => {
  const { client } = renderApp('/')
  client.setQueryData(['groupbuys', 'public'], { items: [] })
  await screen.findByRole('link', { name: '로그인' })
  expect(client.getQueryData(['groupbuys', 'public'])).toEqual({ items: [] })
})

test('시작 시 me가 5xx·네트워크 오류면 anonymous로 정하지 않는다: 보호 화면에서 /login으로 보내지 않고 다시 시도하게 한다', async () => {
  for (const fail of [() => new HttpResponse(null, { status: 503 }), () => HttpResponse.error()]) {
    mockState.sessionMemberId = 1
    server.use(http.get('/api/auth/me', fail, { once: true }))
    const { router, unmount } = renderApp('/me')
    await screen.findByText('잠시 문제가 생겼어요. 다시 시도해 주세요.')
    expect(router.state.location.pathname).toBe('/me')
    expect(screen.queryByRole('link', { name: '로그인' })).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    await screen.findByRole('button', { name: '로그아웃' })
    expect(router.state.location.pathname).toBe('/me')
    unmount()
  }
})

test('시작 시 느린 me(401)가 로그인 성공 뒤에 도착해도 로그인 상태를 덮어쓰지 않는다', async () => {
  server.use(http.get('/api/auth/me', async () => { await delay(300); return HttpResponse.json({ title: 'UNAUTHENTICATED', status: 401 }, { status: 401 }) }, { once: true }))
  renderApp('/login')
  await userEvent.type(await screen.findByLabelText('이메일'), 'organizer@school.ac.kr')
  await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234')
  await userEvent.click(screen.getByRole('button', { name: '로그인' }))
  await screen.findByRole('button', { name: '로그아웃' })
  await new Promise((r) => setTimeout(r, 400))
  screen.getByRole('button', { name: '로그아웃' })
})

test('401이 연달아 와도(예: 화면 이동 시 상세와 읽지 않은 수) 만료 안내가 사라지지 않는다', async () => {
  mockState.sessionMemberId = 1
  renderApp('/me')
  await screen.findByRole('button', { name: '로그아웃' })
  mockState.sessionExpired = true
  await Promise.all([get('/api/auth/me').catch(() => {}), get('/api/notifications/unread-count').catch(() => {})])
  await screen.findByText('로그인이 필요해요. 입력하던 내용은 저장되지 않아요.')
})
