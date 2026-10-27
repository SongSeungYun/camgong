import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'
import { mockState } from './state'

export const worker = setupWorker(...handlers)

// 개발·e2e에서 시나리오(만료·잠금)를 켜고 끌 수 있게 노출한다.
;(window as unknown as { __mock: typeof mockState }).__mock = mockState
