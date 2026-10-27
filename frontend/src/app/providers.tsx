import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { SessionProvider } from '../features/auth/session'
import { ApiError } from '../shared/api/client'

/** 5xx·네트워크 오류만 한 번 재시도한다. 4xx는 서버의 판단이라 재시도하지 않는다. */
export const createQueryClient = (opts: { retry?: false } = {}) =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: opts.retry ?? ((count, e) => count < 1 && e instanceof ApiError && (e.status === 0 || e.status >= 500)), refetchOnWindowFocus: false },
    },
  })

export function AppProviders({ client, children }: { client: QueryClient; children: ReactNode }) {
  return (
    <QueryClientProvider client={client}>
      <SessionProvider>{children}</SessionProvider>
    </QueryClientProvider>
  )
}
