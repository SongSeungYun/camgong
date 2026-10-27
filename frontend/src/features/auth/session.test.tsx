import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test } from 'vitest'
import { get } from '../../shared/api/client'
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
