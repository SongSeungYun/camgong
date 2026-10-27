import { useNavigate } from 'react-router'
import { GroupBuyForm } from '../features/groupbuy/form/GroupBuyForm'
import { useCreateGroupBuy } from '../features/groupbuy/form/useGroupBuyMutations'
import { Layout } from '../shared/ui'

export default function CreatePage() {
  const create = useCreateGroupBuy()
  const navigate = useNavigate()
  return (
    <Layout>
      <h1>공구 열기</h1>
      <GroupBuyForm submitLabel="공구 열기" onSubmit={async (body) => navigate(`/groupbuys/${(await create.mutateAsync(body)).id}`)} />
    </Layout>
  )
}
