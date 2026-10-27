import { useState } from 'react'
import { Link, useParams } from 'react-router'
import type { CheckFilter, CheckKind } from '../features/check/api'
import { CheckRow } from '../features/check/CheckRow'
import c from '../features/check/Check.module.css'
import { useCheckSheet } from '../features/check/useCheckSheet'
import { useSetConfirmed } from '../features/check/useSetConfirmed'
import chip from '../features/groupbuy/list/GroupBuyCard.module.css'
import { ApiError } from '../shared/api/client'
import { errorMessage } from '../shared/lib/errorMessage'
import { formatWon } from '../shared/lib/money'
import { Layout, StateView } from '../shared/ui'

const FILTERS: { value: CheckFilter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'unpaid', label: '미입금' },
  { value: 'unreceived', label: '미수령' },
]
// 이 오류들은 다시 시도해도 같으므로 안내 + 상세로 보낸다.
const BLOCKING = ['ORGANIZER_ONLY', 'NOT_CHECKABLE']

export default function CheckPage() {
  const id = Number(useParams().id)
  const [filter, setFilter] = useState<CheckFilter>('all')
  const q = useCheckSheet(id, filter)
  const set = useSetConfirmed(id)
  const toApi = (e: unknown) => (e instanceof ApiError ? e : e ? new ApiError(0) : null)
  const loadErr = toApi(q.error)
  const setErr = toApi(set.error)
  const blocking = [loadErr, setErr].find((e) => e && e.code && BLOCKING.includes(e.code))
  const backToDetail = <Link to={`/groupbuys/${id}`}>공구 상세로</Link>

  if (!Number.isInteger(id) || (loadErr?.status === 404 && !q.data)) {
    return <Layout><p>찾을 수 없는 공구예요.</p><Link to="/">목록으로</Link></Layout>
  }
  if (blocking) return <Layout><p role="alert">{errorMessage(blocking)}</p>{backToDetail}</Layout>

  const sheet = q.data
  const onSet = (memberId: number, kind: CheckKind, confirmed: boolean) => {
    if (!set.isPending) set.mutate({ memberId, kind, confirmed })
  }
  return (
    <Layout>
      <h1>참여자 체크</h1>
      <p>{backToDetail}</p>
      <StateView state={q.isPending ? 'loading' : q.isError && !sheet ? 'error' : 'ready'} onRetry={() => void q.refetch()}>
        {sheet && (
          <>
            <section className={c.summary} aria-label="요약">
              <p>입금 완료 {sheet.summary.paidCount}명 / {sheet.summary.participantCount}명</p>
              <p>수령 완료 {sheet.summary.receivedCount}명 / {sheet.summary.participantCount}명</p>
              <p>입금 확인 {formatWon(sheet.summary.amountConfirmed)} / {formatWon(sheet.summary.amountDue)}</p>
            </section>
            <div className={chip.chips} role="group" aria-label="보기">
              {FILTERS.map((f) => (
                <button key={f.value} type="button" className={chip.chip} aria-pressed={filter === f.value} onClick={() => setFilter(f.value)}>
                  {f.label}
                </button>
              ))}
            </div>
            {setErr && <p role="alert">{errorMessage(setErr)}</p>}
            {sheet.rows.length === 0 ? (
              <p>{sheet.summary.participantCount === 0 ? '아직 참여자가 없어요.' : '해당하는 참여자가 없어요.'}</p>
            ) : (
              <ul className={c.list}>
                {sheet.rows.map((r) => (
                  <CheckRow
                    key={r.memberId}
                    row={r}
                    pendingKind={set.isPending && set.variables.memberId === r.memberId ? set.variables.kind : null}
                    onSet={(kind, confirmed) => onSet(r.memberId, kind, confirmed)}
                  />
                ))}
              </ul>
            )}
          </>
        )}
      </StateView>
    </Layout>
  )
}
