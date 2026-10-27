import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, get, post, setUnauthenticatedHandler } from '../../shared/api/client'
import type { Member } from '../../shared/api/types'

type Status = 'checking' | 'anonymous' | 'authenticated'

interface Session {
  status: Status
  /** 이메일·비밀번호는 보관하지 않는다(FR-019) */
  member: Member | null
  /** 사용 중 세션이 만료(401)돼 로그인으로 보내졌는지: 로그인 화면이 소실 안내를 보여준다 */
  expired: boolean
  /** 로그인 화면이 만료 안내를 한 번 보여준 뒤 호출한다. 이후 /login을 다시 열어도 나오지 않는다 */
  ackExpired: () => void
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
      ackExpired: () => setState((prev) => (prev.expired ? { ...prev, expired: false } : prev)),
      setMember: (member) => setState({ status: 'authenticated', member, expired: false }),
      // 서버가 로그아웃을 확인한 뒤에만 화면을 비운다. 실패하면 던져서 호출한 쪽이 안내하고 로그인 상태를 유지한다
      // (화면만 로그아웃되고 세션 쿠키가 살아 있는 상태를 만들지 않는다).
      logout: async () => {
        try {
          // 로그아웃 요청의 401은 '이미 로그아웃된 상태'라 만료 안내(expired)를 켜지 않고 로그아웃으로 처리한다
          await post('/api/auth/logout', undefined, { expect401: true })
        } catch (e) {
          if (!(e instanceof ApiError && e.status === 401)) throw e
        }
        clear()
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
