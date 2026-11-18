import { Link } from 'react-router'
import { categoryLabel, type GroupBuyCard as Card } from '../../../shared/api/types'
import { formatWon } from '../../../shared/lib/money'
import { formatKst, remainingText } from '../../../shared/lib/time'
import { Badge, ProgressBar } from '../../../shared/ui'
import s from './GroupBuyCard.module.css'

/** nowMs는 목록을 불러온 시점이다(실시간 갱신 없음). 서버 텍스트는 JSX 텍스트로만 렌더링한다(FR-010). */
/** showSchool(009): 비로그인 목록에서만 공구의 학교를 보인다(로그인하면 늘 내 학교라 넣지 않는다) */
export function GroupBuyCard({ item, nowMs, showSchool = false }: { item: Card; nowMs: number; showSchool?: boolean }) {
  return (
    <Link to={`/groupbuys/${item.id}`} className={s.card}>
      <div className={s.top}>
        <span className={s.meta}>{categoryLabel(item.category)}</span>
        <Badge status={item.status} />
      </div>
      <h2 className={s.title}>{item.title}</h2>
      <p className={s.meta}>{item.itemName}</p>
      {showSchool && item.school && <p className={s.meta}>학교 · {item.school}</p>}
      <p className={s.meta}>{formatWon(item.unitPrice)} / 개</p>
      <ProgressBar value={item.securedQuantity} max={item.totalQuantity} />
      <p className={s.meta}>{item.securedQuantity}/{item.totalQuantity}</p>
      {item.myQuantity !== undefined && <p className={s.meta}>{`내 수량 ${item.myQuantity}개`}</p>}
      <p className={s.meta}>
        <span>{formatKst(item.deadline)}</span> · <span>{remainingText(item.deadline, nowMs)}</span>
      </p>
    </Link>
  )
}
