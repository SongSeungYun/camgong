import type { CheckRow as Row } from '../../shared/api/types'
import { formatWon } from '../../shared/lib/money'
import { formatKst } from '../../shared/lib/time'
import { Button } from '../../shared/ui'
import type { CheckKind } from './api'
import s from './Check.module.css'

interface Props {
  row: Row
  /** 이 행에서 처리 중인 항목(연타 방지) */
  pendingKind: CheckKind | null
  onSet: (kind: CheckKind, confirmed: boolean) => void
}

const LABEL: Record<CheckKind, string> = { payment: '입금', receipt: '수령' }

function Toggle({ row, kind, pendingKind, onSet }: Props & { kind: CheckKind }) {
  const at = kind === 'payment' ? row.paidConfirmedAt : row.receivedConfirmedAt
  return (
    <div className={s.toggle}>
      <span>{at ? `${LABEL[kind]} 확인됨 (${formatKst(at)})` : `${LABEL[kind]} 확인 전`}</span>
      <Button variant="secondary" pending={pendingKind === kind} onClick={() => onSet(kind, !at)}>
        {at ? `${LABEL[kind]} 확인 취소` : `${LABEL[kind]} 확인`}
      </Button>
    </div>
  )
}

/** 이름은 서버 텍스트라 JSX 텍스트로만 렌더링한다(FR-010). */
export function CheckRow(props: Props) {
  const { row } = props
  return (
    <li className={s.row} aria-label={row.name}>
      <p className={s.who}>
        <strong>{row.name}</strong> <span>{row.quantity}개</span> <span>{formatWon(row.amount)}</span>
      </p>
      {row.receivedWithoutPayment && <p className={s.warn}>입금 없이 수령됨</p>}
      <Toggle {...props} kind="payment" />
      <Toggle {...props} kind="receipt" />
    </li>
  )
}
