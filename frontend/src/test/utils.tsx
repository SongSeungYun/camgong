import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AppProviders, createQueryClient } from '../app/providers'
import { routes } from '../app/router'

/** 테스트마다 새 QueryClient·라우터·세션으로 앱을 렌더한다(캐시·세션이 새지 않는다). */
export function renderApp(path = '/') {
  const client = createQueryClient()
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const utils = render(
    <AppProviders client={client}>
      <RouterProvider router={router} />
    </AppProviders>,
  )
  return { ...utils, client, router }
}
