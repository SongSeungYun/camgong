import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from '../mocks/server'
import { mockState } from '../mocks/state'

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  mockState.reset()
  cleanup()
  server.resetHandlers()
  // 쿼리 캐시·세션은 테스트마다 새 QueryClient/렌더 래퍼로 만든다(Phase 2 test utils).
})
afterAll(() => server.close())
