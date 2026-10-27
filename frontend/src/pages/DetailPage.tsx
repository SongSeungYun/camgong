import { Link, useParams } from 'react-router'
import { GroupBuyDetail } from '../features/groupbuy/detail/GroupBuyDetail'
import { useGroupBuyDetail } from '../features/groupbuy/detail/useGroupBuyDetail'
import { ApiError } from '../shared/api/client'
import { Layout, StateView } from '../shared/ui'

function NotFound() {
  return (
    <Layout>
      <p>찾을 수 없는 공구예요.</p>
      <Link to="/">목록으로</Link>
    </Layout>
  )
}

export default function DetailPage() {
  const id = Number(useParams().id)
  const q = useGroupBuyDetail(id)
  if (!Number.isInteger(id)) return <NotFound />
  if (q.error instanceof ApiError && q.error.status === 404) return <NotFound />
  return (
    <Layout>
      <StateView state={q.isPending ? 'loading' : q.isError ? 'error' : 'ready'} onRetry={() => void q.refetch()}>
        {q.data && <GroupBuyDetail d={q.data} nowMs={q.dataUpdatedAt} />}
      </StateView>
    </Layout>
  )
}
