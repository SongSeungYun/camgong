import { uniqueById } from '../shared/lib/uniqueById'
import { useNavigate } from 'react-router'
import { NotificationItem } from '../features/notification/NotificationItem'
import s from '../features/notification/Notification.module.css'
import { useMarkRead, useNotifications } from '../features/notification/hooks'
import type { AppNotification } from '../shared/api/types'
import { Button, Layout, StateView } from '../shared/ui'

export default function NotificationsPage() {
  const q = useNotifications()
  const read = useMarkRead()
  const navigate = useNavigate()
  const items = uniqueById(q.data?.pages.flatMap((p) => p.items) ?? [])
  const state = q.isPending ? 'loading' : q.isError && items.length === 0 ? 'error' : items.length === 0 ? 'empty' : 'ready'

  // 읽음 처리가 실패해도 이동은 막지 않는다(읽음은 부가 기능).
  const open = (n: AppNotification) => {
    const go = () => n.linkable && navigate(`/groupbuys/${n.groupBuyId}`)
    if (n.read) return void go()
    read.mutate(n.id, { onSettled: go })
  }

  return (
    <Layout>
      <h1>알림</h1>
      <StateView state={state} emptyText="아직 알림이 없어요." onRetry={() => void q.refetch()}>
        <ul className={s.list}>
          {items.map((n) => (
            <NotificationItem key={n.id} n={n} onOpen={open} />
          ))}
        </ul>
        {q.isFetchNextPageError && <p role="alert">더 불러오지 못했어요. 다시 시도해 주세요.</p>}
        {q.hasNextPage && (
          <Button variant="secondary" pending={q.isFetchingNextPage} onClick={() => void q.fetchNextPage()}>
            더 보기
          </Button>
        )}
      </StateView>
    </Layout>
  )
}
