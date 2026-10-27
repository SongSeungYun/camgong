import { Outlet, useRouteError, type RouteObject } from 'react-router'
import { Layout, StateView } from '../shared/ui'
import { Header } from './Header'
import { RequireAuth } from './RequireAuth'

const stub = (name: string): RouteObject['lazy'] => async () => {
  const { Stub } = await import('../pages/Stub')
  return { element: <Stub name={name} /> }
}

function Root() {
  return (
    <>
      <Header />
      <Outlet />
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
      { path: '/', lazy: stub('목록') },
      { path: '/signup', lazy: stub('가입') },
      { path: '/login', lazy: stub('로그인') },
      { path: '/verify', lazy: stub('인증 확인') },
      {
        element: <RequireAuth />,
        children: [
          { path: '/groupbuys/new', lazy: stub('공구 개설') },
          { path: '/groupbuys/:id', lazy: stub('공구 상세') },
          { path: '/groupbuys/:id/edit', lazy: stub('공구 수정') },
          { path: '/groupbuys/:id/check', lazy: stub('체크표') },
          { path: '/me', lazy: stub('내 활동') },
          { path: '/notifications', lazy: stub('알림함') },
        ],
      },
      { path: '*', lazy: () => import('../pages/NotFoundPage').then((m) => ({ Component: m.default })) },
    ],
  },
]
