import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { get, post, setUnauthenticatedHandler } from '../../shared/api/client'
import type { Member } from '../../shared/api/types'

type Status = 'checking' | 'anonymous' | 'authenticated'

interface Session {
  status: Status
  /** 이메일·비밀번호는 보관하지 않는다(FR-019) */
  member: Member | null
  /** 사용 중 세션이 만료(401)돼 로그인으로 보내졌는지: 로그인 화면이 소실 안내를 보여준다 */
  expired: boolean
  /** 로그인·인증 성공 응답으로 세션을 채운다 */
  setMember: (m: Member) => void
  logout: () => Promise<void>
}

const Ctx = createContext<Session | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<{ status: Status; member: Member | null; expired: boolean }>({ status: 'checking', member: null, expired: false })

  const clear = useCallback(
    (expired = false) => {
      setState((prev) => ({ status: 'anonymous', member: null, expired: expired && prev.status === 'authenticated' }))
      queryClient.clear()
    },
    [queryClient],
  )

  useEffect(() => {
    setUnauthenticatedHandler(() => clear(true))
    let alive = true
    get<{ member: Member }>('/api/auth/me', { expect401: true }).then(
      (r) => alive && setState({ status: 'authenticated', member: r.member, expired: false }),
      () => alive && setState({ status: 'anonymous', member: null, expired: false }), // 비로그인 401은 정상 응답(공개 화면의 캐시를 비우지 않는다)
    )
    return () => {
      alive = false
      setUnauthenticatedHandler(() => {})
    }
  }, [clear])

  const value = useMemo<Session>(
    () => ({
      ...state,
      setMember: (member) => setState({ status: 'authenticated', member, expired: false }),
      logout: async () => {
        try {
          await post('/api/auth/logout')
        } finally {
          clear() // 서버 호출이 실패해도 화면은 로그아웃 상태로
        }
      },
    }),
    [state, clear],
  )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useSession(): Session {
  const v = useContext(Ctx)
  if (!v) throw new Error('SessionProvider 안에서 써야 한다')
  return v
}
