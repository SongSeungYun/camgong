import type { AppNotification } from '../../shared/api/types'
import { formatKst } from '../../shared/lib/time'
import s from './Notification.module.css'

const FIELD_LABELS: Record<string, string> = {
  title: '제목',
  description: '설명',
  itemName: '상품명',
  category: '카테고리',
  itemUrl: '구매처 링크',
  unitPrice: '개당 가격',
  totalQuantity: '총 수량',
  deadline: '마감',
  organizerQuantity: '개설자 수량',
}
/** 알 수 없는 항목 이름은 그대로 보여 화면이 멈추지 않게 한다. */
export const fieldLabel = (f: string) => FIELD_LABELS[f] ?? f

/** 제목은 서버 텍스트라 JSX 텍스트로만 렌더링한다(FR-010). */
function message(n: AppNotification): string {
  const t = `「${n.groupBuyTitle}」`
  switch (n.type) {
    case 'GROUPBUY_COMPLETED': return `${t} 모집이 완료됐어요.`
    case 'GROUPBUY_FAILED': return `${t} 모집이 실패했어요.`
    case 'GROUPBUY_CHANGED': return `${t} 정보가 수정됐어요.`
    case 'GROUPBUY_CANCELLED': return `${t} 공구가 취소됐어요.`
    default: return `${t} 새 소식이 있어요.`
  }
}

export function NotificationItem({ n, onOpen }: { n: AppNotification; onOpen: (n: AppNotification) => void }) {
  return (
    <li>
      <button type="button" className={s.item} data-unread={!n.read} onClick={() => onOpen(n)}>
        <span className={s.msg}>{message(n)}</span>
        {n.type === 'GROUPBUY_CHANGED' && n.changedFields && n.changedFields.length > 0 && (
          <span>바뀐 항목: {n.changedFields.map(fieldLabel).join(', ')}</span>
        )}
        <span className={s.meta}>
          {!n.read && <strong>새 알림 </strong>}
          {formatKst(n.createdAt)}
        </span>
      </button>
    </li>
  )
}
