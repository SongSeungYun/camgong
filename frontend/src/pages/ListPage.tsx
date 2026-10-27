import { useLocation } from 'react-router'
import { GroupBuyCard } from '../features/groupbuy/list/GroupBuyCard'
import s from '../features/groupbuy/list/GroupBuyCard.module.css'
import { ListFilters } from '../features/groupbuy/list/ListFilters'
import { useListParams } from '../features/groupbuy/list/listParams'
import { useGroupBuyList } from '../features/groupbuy/list/useGroupBuyList'
import { Button, Layout, StateView } from '../shared/ui'

export default function ListPage() {
  const { params, qInput, setQInput, setParams } = useListParams()
  const q = useGroupBuyList(params)
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  const items = q.data?.pages.flatMap((p) => p.items) ?? []
  // FR-014: 정렬을 뺀 조건(검색어·카테고리·기본이 아닌 상태)이 하나라도 걸려 있으면 '결과 없음'
  const filtered = !!params.q || params.category !== null || params.status !== 'OPEN'
  const state = q.isPending ? 'loading' : q.isError && items.length === 0 ? 'error' : items.length === 0 ? 'empty' : 'ready'

  return (
    <Layout>
      <h1>공구 목록</h1>
      {notice && <p role="status">{notice}</p>}
      <ListFilters params={params} qInput={qInput} onQ={setQInput} onChange={setParams} />
      <StateView
        state={state}
        onRetry={() => void q.refetch()}
        emptyText={filtered ? '검색 결과가 없어요. 다른 검색어나 조건을 써보세요.' : '아직 올라온 공구가 없어요.'}
      >
        <ul className={s.list}>
          {items.map((it) => (
            <li key={it.id}>
              <GroupBuyCard item={it} nowMs={q.dataUpdatedAt} />
            </li>
          ))}
        </ul>
        {q.isFetchNextPageError && <p role="alert">더 불러오지 못했어요. 다시 시도해 주세요.</p>}
        {q.hasNextPage && (
          <Button className={s.more} variant="secondary" pending={q.isFetchingNextPage} onClick={() => void q.fetchNextPage()}>
            더 보기
          </Button>
        )}
      </StateView>
    </Layout>
  )
}
