import type { ComponentType } from 'react'
import { Link, Outlet, ScrollRestoration, useRouteError, type RouteObject } from 'react-router'
import { Layout, StateView } from '../shared/ui'
import { Header } from './Header'
import { RequireAuth } from './RequireAuth'
import s from './Footer.module.css'

const page = (load: () => Promise<{ default: ComponentType }>): RouteObject['lazy'] => async () => ({ Component: (await load()).default })

function Root() {
  return (
    <>
      <Header />
      <Outlet />
      <Footer />
      <ScrollRestoration />
    </>
  )
}

/** 모든 화면 아래. 처리방침 링크는 다른 고지와 구분되게 굵게 둔다(시행령 제31조 제2항, 작성지침) */
function Footer() {
  return (
    <footer className={s.footer}>
      <Link to="/privacy" className={s.privacy}>개인정보 처리방침</Link>
    </footer>
  )
}

/** 최상위 에러 경계: 화면이 멈추지 않고 일반 문구만 보인다(서버·스택 정보 비노출). */
function RootError() {
  console.error(useRouteError())
  return (
    <Layout>
      <StateView state="error" onRetry={() => location.reload()} />
    </Layout>
  )
}

// 주소는 contracts/ui-api-map.md 1절. 
export const routes: RouteObject[] = [
  {
    element: <Root />,
    errorElement: <RootError />,
    children: [
      { path: '/', lazy: page(() => import('../pages/ListPage')) },
      { path: '/signup', lazy: page(() => import('../pages/SignupPage')) },
      { path: '/login', lazy: page(() => import('../pages/LoginPage')) },
      { path: '/verify', lazy: page(() => import('../pages/VerifyPage')) },
      { path: '/password-reset', lazy: page(() => import('../pages/PasswordResetPage')) },
      { path: '/privacy', lazy: page(() => import('../pages/PrivacyPage')) },
      {
        element: <RequireAuth />,
        children: [
          { path: '/groupbuys/new', lazy: page(() => import('../pages/CreatePage')) },
          { path: '/groupbuys/:id', lazy: page(() => import('../pages/DetailPage')) },
          { path: '/groupbuys/:id/edit', lazy: page(() => import('../pages/EditPage')) },
          { path: '/groupbuys/:id/check', lazy: page(() => import('../pages/CheckPage')) },
          { path: '/me', lazy: page(() => import('../pages/MyPage')) },
          { path: '/notifications', lazy: page(() => import('../pages/NotificationsPage')) },
        ],
      },
      { path: '*', lazy: page(() => import('../pages/NotFoundPage')) },
    ],
  },
]
