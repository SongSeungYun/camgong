import { setupServer } from 'msw/node'

// 핸들러는 Phase 2(목 서버 뼈대)에서 채운다.
export const server = setupServer()
