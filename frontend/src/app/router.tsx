import type { ComponentType } from 'react'
import { Outlet, ScrollRestoration, useRouteError, type RouteObject } from 'react-router'
import { Layout, StateView } from '../shared/ui'
import { Header } from './Header'
import { RequireAuth } from './RequireAuth'

const stub = (name: string): RouteObject['lazy'] => async () => {
  const { Stub } = await import('../pages/Stub')
  return { element: <Stub name={name} /> }
}

const page = (load: () => Promise<{ default: ComponentType }>): RouteObject['lazy'] => async () => ({ Component: (await load()).default })

function Root() {
  return (
    <>
      <Header />
      <Outlet />
      <ScrollRestoration />
    </>
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

// 주소는 contracts/ui-api-map.md 1절. 화면은 Phase 3 이후 stub을 실제 페이지로 바꾼다.
export const routes: RouteObject[] = [
  {
    element: <Root />,
    errorElement: <RootError />,
    children: [
      { path: '/', lazy: page(() => import('../pages/ListPage')) },
      { path: '/signup', lazy: page(() => import('../pages/SignupPage')) },
      { path: '/login', lazy: page(() => import('../pages/LoginPage')) },
      { path: '/verify', lazy: page(() => import('../pages/VerifyPage')) },
      {
        element: <RequireAuth />,
        children: [
          { path: '/groupbuys/new', lazy: page(() => import('../pages/CreatePage')) },
          { path: '/groupbuys/:id', lazy: page(() => import('../pages/DetailPage')) },
          { path: '/groupbuys/:id/edit', lazy: page(() => import('../pages/EditPage')) },
          { path: '/groupbuys/:id/check', lazy: stub('체크표') },
          { path: '/me', lazy: page(() => import('../pages/MyPage')) },
          { path: '/notifications', lazy: stub('알림함') },
        ],
      },
      { path: '*', lazy: page(() => import('../pages/NotFoundPage')) },
    ],
  },
]
