import { Link } from 'react-router'
import { Layout } from '../shared/ui'

export default function NotFoundPage() {
  return (
    <Layout>
      <h1>찾을 수 없어요</h1>
      <Link to="/">목록으로</Link>
    </Layout>
  )
}
