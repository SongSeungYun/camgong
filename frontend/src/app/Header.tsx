import { Link, useNavigate } from 'react-router'
import { useSession } from '../features/auth/session'
import { Button } from '../shared/ui'

// 알림 배지 자리는 US7(S10)에서 채운다.
export function Header() {
  const { status, logout } = useSession()
  const navigate = useNavigate()
  return (
    <header style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center', padding: 'var(--space-2) var(--space-4)', borderBottom: '1px solid var(--color-border)' }}>
      <Link to="/" style={{ fontWeight: 700, marginRight: 'auto' }}>캠공</Link>
      {status === 'authenticated' ? (
        <>
          <Link to="/groupbuys/new">공구 열기</Link>
          <Link to="/me">내 활동</Link>
          <Button variant="secondary" onClick={() => logout().then(() => navigate('/login'))}>로그아웃</Button>
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
