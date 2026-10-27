import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useSession } from '../features/auth/session'
import s from '../features/notification/Notification.module.css'
import { useUnreadCount } from '../features/notification/hooks'
import { Button } from '../shared/ui'

/** 로그인했을 때만 마운트돼 로그아웃·401 뒤에는 쿼리도 남지 않는다. 오류·로딩이면 0과 같이 배지를 숨긴다. */
function NotificationBell() {
  const unread = useUnreadCount().data?.unreadCount ?? 0
  return (
    <Link to="/notifications" className={s.bell}>
      알림
      {unread > 0 && <span className={s.badge} aria-label={`읽지 않은 알림 ${unread}개`}>{unread}</span>}
    </Link>
  )
}

export function Header() {
  const { status, logout } = useSession()
  const navigate = useNavigate()
  const [logoutFailed, setLogoutFailed] = useState(false)
  const { pathname } = useLocation()
  useEffect(() => setLogoutFailed(false), [pathname, status]) // 화면을 옮기거나 로그인 상태가 바뀌면 안내를 걷는다
  const [loggingOut, setLoggingOut] = useState(false)
  const onLogout = async () => {
    setLoggingOut(true)
    setLogoutFailed(false)
    try {
      await logout()
      navigate('/login')
    } catch {
      setLogoutFailed(true) // 서버가 확인하지 못했으니 로그인 상태를 유지하고 다시 시도하게 한다
    } finally {
      setLoggingOut(false)
    }
  }
  return (
    <header style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center', flexWrap: 'wrap', padding: 'var(--space-2) var(--space-4)', borderBottom: '1px solid var(--color-border)' }}>
      <Link to="/" style={{ fontWeight: 700, marginRight: 'auto' }}>캠공</Link>
      {status === 'authenticated' ? (
        <>
          <Link to="/groupbuys/new">공구 열기</Link>
          <Link to="/me">내 활동</Link>
          <NotificationBell />
          <Button variant="secondary" pending={loggingOut} onClick={() => void onLogout()}>로그아웃</Button>
          {logoutFailed && <p role="alert" style={{ flexBasis: '100%', margin: 0 }}>로그아웃을 서버에 알리지 못했어요. 다시 시도해 주세요.</p>}
        </>
      ) : status === 'anonymous' ? (
        <>
          <Link to="/login">로그인</Link>
          <Link to="/signup">가입</Link>
        </>
      ) : null}
    </header>
  )
}
