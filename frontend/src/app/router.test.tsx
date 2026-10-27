import { screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { mockState } from '../mocks/state'
import { renderApp } from '../test/utils'

test('공개 라우트는 비로그인으로 열린다', async () => {
  renderApp('/')
  await screen.findByRole('heading', { name: '공구 목록' })
})

test('보호 라우트는 비로그인이면 /login으로 보낸다', async () => {
  const { router } = renderApp('/groupbuys/1')
  await screen.findByRole('heading', { name: '로그인' })
  expect(router.state.location.pathname).toBe('/login')
})

test('로그인 상태면 보호 라우트가 열린다', async () => {
  mockState.sessionMemberId = 1
  renderApp('/groupbuys/1')
  await screen.findByRole('heading', { name: '공구 상세' })
})

test('me 확인 중에는 로그인으로 보내지 않는다(깜빡임 없음)', async () => {
  mockState.sessionMemberId = 1
  const { router } = renderApp('/me')
  const seen: string[] = [router.state.location.pathname]
  router.subscribe((st) => seen.push(st.location.pathname))
  await screen.findByRole('heading', { name: '내 활동' })
  expect(seen).not.toContain('/login')
})

test('존재하지 않는 라우트는 찾을 수 없음', async () => {
  renderApp('/nope')
  await screen.findByRole('heading', { name: '찾을 수 없어요' })
})
