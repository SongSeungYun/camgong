import { Layout } from '../shared/ui'

// ponytail: Phase 3 이후 각 화면이 실제 페이지로 교체한다.
export function Stub({ name }: { name: string }) {
  return (
    <Layout>
      <h1>{name}</h1>
    </Layout>
  )
}
