import { uniqueById } from '../shared/lib/uniqueById'
import { useSearchParams } from 'react-router'
import type { MyRole } from '../features/groupbuy/api'
import { GroupBuyCard } from '../features/groupbuy/list/GroupBuyCard'
import s from '../features/groupbuy/list/GroupBuyCard.module.css'
import { useMyGroupBuys } from '../features/groupbuy/mine/useMyGroupBuys'
import { Button, Layout, StateView } from '../shared/ui'

const TABS: { role: MyRole; param: string | null; label: string; empty: string }[] = [
  { role: 'organizer', param: null, label: '내가 연 공구', empty: '아직 연 공구가 없어요.' },
  { role: 'participant', param: 'participant', label: '참여한 공구', empty: '아직 참여한 공구가 없어요.' },
]

export default function MyPage() {
  const [sp, setSp] = useSearchParams()
  const tab = TABS.find((t) => t.param === sp.get('tab')) ?? TABS[0]!
  const q = useMyGroupBuys(tab.role)
  const items = uniqueById(q.data?.pages.flatMap((p) => p.items) ?? [])
  const state = q.isPending ? 'loading' : q.isError && items.length === 0 ? 'error' : items.length === 0 ? 'empty' : 'ready'

  return (
    <Layout>
      <h1>내 활동</h1>
      <div role="tablist" className={s.chips}>
        {TABS.map((t) => (
          <button
            key={t.role}
            id={`tab-${t.role}`}
            role="tab"
            type="button"
            className={s.chip}
            aria-selected={t === tab}
            aria-controls="my-panel"
            onClick={() => setSp(t.param ? { tab: t.param } : {}, { replace: true })}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id="my-panel" aria-labelledby={`tab-${tab.role}`}>
      <StateView state={state} emptyText={tab.empty} onRetry={() => void q.refetch()}>
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
      </div>
    </Layout>
  )
}
