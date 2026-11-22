import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'

/**
 * 이동해 온 안내(navigate state의 notice): 한 번 보여주고 기록(state)에서 지운다(새로고침·뒤로 가기에 다시 뜨지 않게).
 * 처음부터 글이 든 status는 낭독이 불안정해 안내로 포커스를 옮긴다. ref는 tabIndex={-1}인 안내 요소에 단다.
 */
export function useArrivalNotice() {
  const location = useLocation()
  const navigate = useNavigate()
  const [notice] = useState(() => (location.state as { notice?: string } | null)?.notice)
  const ref = useRef<HTMLParagraphElement>(null)
  useEffect(() => {
    if (!notice) return
    ref.current?.focus()
    void navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: null })
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 처음 한 번만
  }, [])
  return { notice, ref }
}
