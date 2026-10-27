import { Link, useNavigate, useParams } from 'react-router'
import { useGroupBuyDetail } from '../features/groupbuy/detail/useGroupBuyDetail'
import { EMPTY_VALUES, type FormValues } from '../features/groupbuy/form/fieldMap'
import { GroupBuyForm } from '../features/groupbuy/form/GroupBuyForm'
import { useUpdateGroupBuy } from '../features/groupbuy/form/useGroupBuyMutations'
import { ApiError } from '../shared/api/client'
import type { GroupBuyDetail } from '../shared/api/types'
import { toKstLocalInput } from '../shared/lib/time'
import { Layout, StateView } from '../shared/ui'

const valuesOf = (d: GroupBuyDetail): FormValues => ({
  ...EMPTY_VALUES,
  title: d.title,
  description: d.description ?? '',
  category: d.category,
  itemName: d.itemName,
  itemUrl: d.itemUrl ?? '',
  unitPrice: String(d.unitPrice),
  totalQuantity: String(d.totalQuantity),
  // 개설자의 myParticipation.quantity가 곧 내 수량이다
  organizerQuantity: String(d.myParticipation?.quantity ?? ''),
  deadline: toKstLocalInput(d.deadline),
  openChatUrl: d.openChatUrl ?? '',
})

export default function EditPage() {
  const id = Number(useParams().id)
  const q = useGroupBuyDetail(id)
  const update = useUpdateGroupBuy(id)
  const navigate = useNavigate()
  const back = <Link to={`/groupbuys/${id}`}>상세로</Link>

  if (!Number.isInteger(id) || (q.error instanceof ApiError && q.error.status === 404)) return <Layout><p>찾을 수 없는 공구예요.</p><Link to="/">목록으로</Link></Layout>
  return (
    <Layout>
      <h1>공구 수정</h1>
      <StateView state={q.isPending ? 'loading' : q.isError ? 'error' : 'ready'} onRetry={() => void q.refetch()}>
        {q.data && !q.data.isOrganizer && <><p>개설자만 쓸 수 있어요.</p>{back}</>}
        {q.data && q.data.isOrganizer && q.data.status !== 'OPEN' && <><p>이미 모집이 끝났거나 마감이 지나서 수정·취소할 수 없어요.</p>{back}</>}
        {q.data && q.data.isOrganizer && q.data.status === 'OPEN' && (
          <GroupBuyForm
            key={q.data.id}
            submitLabel="수정 저장"
            defaultValues={valuesOf(q.data)}
            originalDeadline={q.data.deadline}
            notes={
              <>
                {q.data.myParticipation === null && <p role="status">내 수량을 불러오지 못했어요. 직접 입력해 주세요.</p>}
                <p>마감은 늘리기만 할 수 있어요.</p>
                <p>총 수량은 확보 수량({q.data.securedQuantity}개) 이상이어야 해요.</p>
              </>
            }
            onSubmit={async (body) => {
              await update.mutateAsync(body)
              navigate(`/groupbuys/${id}`)
            }}
          />
        )}
      </StateView>
    </Layout>
  )
}
