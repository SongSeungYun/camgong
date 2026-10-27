import { Navigate, Outlet } from 'react-router'
import { useSession } from '../features/auth/session'
import { StateView } from '../shared/ui'

/** me 응답 전에는 확인 중만 보여 화면이 깜빡이지 않게 한다. 로그인 후에는 항상 `/`로 간다. */
export function RequireAuth() {
  const { status } = useSession()
  if (status === 'checking') return <StateView state="loading" />
  if (status === 'anonymous') return <Navigate to="/login" replace />
  return <Outlet />
}
