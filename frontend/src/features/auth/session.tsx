import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ApiError, get, post, setUnauthenticatedHandler } from '../../shared/api/client'
import type { Member } from '../../shared/api/types'

/** error: 시작 시 me가 401이 아닌 오류(5xx·네트워크)라 로그인 여부를 모른다 */
type Status = 'checking' | 'anonymous' | 'authenticated' | 'error'

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
  /** status가 error일 때 me를 다시 확인한다 */
  retry: () => void
  logout: () => Promise<void>
  /** 서버 세션이 이미 끝났을 때(탈퇴 성공) 화면 세션과 캐시만 비운다. 만료 안내는 켜지 않는다 */
  forget: () => void
}

const Ctx = createContext<Session | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<{ status: Status; member: Member | null; expired: boolean }>({ status: 'checking', member: null, expired: false })

  // 세대 번호: 로그인·로그아웃·언마운트 뒤에 도착한 옛 me 응답이 세션을 덮어쓰지 않게 한다
  const gen = useRef(0)
  // 화면 세션에 마지막으로 들어온 회원. 개인 쿼리 키에 회원 id가 없어서, 다른 회원이 들어오면 이전 회원의 조회 캐시를 지운다(T028).
  // 비로그인 401만으로는 지우지 않으니(공개 화면 캐시 유지) 다음 회원이 정해질 때 여기서 한 번에 판단한다.
  // mutation 캐시는 남긴다(VerifyPage가 m.isSuccess로 화면을 정한다)
  const owner = useRef<number | null>(null)
  const adopt = useCallback(
    (member: Member) => {
      if (owner.current !== null && owner.current !== member.id) queryClient.removeQueries()
      owner.current = member.id
      setState({ status: 'authenticated', member, expired: false })
    },
    [queryClient],
  )

  const clear = useCallback(
    (expired = false) => {
      gen.current++
      // 이미 비운 뒤 다른 요청의 401이 또 와도 처음 정한 만료 안내를 유지한다
      setState((prev) => ({ status: 'anonymous', member: null, expired: prev.status === 'authenticated' ? expired : prev.expired }))
      queryClient.clear()
      owner.current = null
    },
    [queryClient],
  )

  const checkMe = useCallback(() => {
    const mine = ++gen.current
    const ok = () => gen.current === mine
    setState({ status: 'checking', member: null, expired: false })
    get<{ member: Member }>('/api/auth/me', { expect401: true }).then(
      (r) => ok() && adopt(r.member),
      // 비로그인 401은 정상 응답(공개 화면의 캐시를 비우지 않는다). 그 밖의 오류는 로그인 여부를 모르니 /login으로 보내지 않는다
      (e) => ok() && setState({ status: e instanceof ApiError && e.status === 401 ? 'anonymous' : 'error', member: null, expired: false }),
    )
  }, [adopt])

  useEffect(() => {
    setUnauthenticatedHandler(() => clear(true))
    checkMe()
    return () => {
      gen.current++
      setUnauthenticatedHandler(() => {})
    }
  }, [clear, checkMe])

  const value = useMemo<Session>(
    () => ({
      ...state,
      ackExpired: () => setState((prev) => (prev.expired ? { ...prev, expired: false } : prev)),
      setMember: (member) => {
        gen.current++
        adopt(member)
      },
      retry: checkMe,
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
      forget: () => clear(),
    }),
    [state, clear, checkMe, adopt],
  )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useSession(): Session {
  const v = useContext(Ctx)
  if (!v) throw new Error('SessionProvider 안에서 써야 한다')
  return v
}
