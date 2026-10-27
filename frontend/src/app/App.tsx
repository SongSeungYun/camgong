import { createBrowserRouter, RouterProvider } from 'react-router'
import { AppProviders, createQueryClient } from './providers'
import { routes } from './router'

const client = createQueryClient()
const router = createBrowserRouter(routes)

export function App() {
  return (
    <AppProviders client={client}>
      <RouterProvider router={router} />
    </AppProviders>
  )
}
