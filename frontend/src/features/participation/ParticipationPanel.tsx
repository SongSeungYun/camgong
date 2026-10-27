import { useState } from 'react'
import { ApiError } from '../../shared/api/client'
import type { GroupBuyDetail } from '../../shared/api/types'
import { errorMessage } from '../../shared/lib/errorMessage'
import { amount, formatWon } from '../../shared/lib/money'
import { Button, ConfirmDialog, TextField } from '../../shared/ui'
import { visibleActions } from '../groupbuy/detail/visibleActions'
import { useChangeQuantity, useJoin, useLeave } from './useParticipation'

type Mode = 'join' | 'change'

/** 참여하기 / 수량 변경 / 참여 취소. 노출은 visibleActions(편의), 최종 허용은 서버 응답. */
export function ParticipationPanel({ d }: { d: GroupBuyDetail }) {
  const v = visibleActions(d)
  const [mode, setMode] = useState<Mode | null>(null)
  const [leaving, setLeaving] = useState(false)
  // 안내는 여기에 둔다: 거부로 상세가 바뀌어 폼이 사라져도 문구는 남아야 한다
  const [message, setMessage] = useState<string | null>(null)
  const leave = useLeave(d.id)

  const open = (m: Mode) => {
    setMessage(null)
    setMode(m)
  }
  return (
    <>
      {v.join && mode !== 'join' && <Button onClick={() => open('join')}>참여하기</Button>}
      {v.changeParticipation && mode !== 'change' && <Button variant="secondary" onClick={() => open('change')}>수량 변경</Button>}
      {v.changeParticipation && <Button variant="secondary" onClick={() => { setMessage(null); setLeaving(true) }}>참여 취소</Button>}
      {mode && (v.join || v.changeParticipation) && <QuantityForm key={mode} mode={mode} d={d} onDone={() => setMode(null)} onMessage={setMessage} />}
      {message && <p role="alert" style={{ flexBasis: '100%' }}>{message}</p>}
      <ConfirmDialog
        open={leaving}
        title="참여를 취소할까요?"
        message="취소하면 되돌릴 수 없어요."
        confirmLabel="참여 취소하기"
        cancelLabel="돌아가기"
        pending={leave.isPending}
        onCancel={() => setLeaving(false)}
        onConfirm={() =>
          leave.mutate(undefined, {
            onError: (e) => setMessage(errorMessage(e instanceof ApiError ? e : new ApiError(0))),
            onSettled: () => setLeaving(false),
          })
        }
      />
    </>
  )
}

function QuantityForm({ mode, d, onDone, onMessage }: { mode: Mode; d: GroupBuyDetail; onDone: () => void; onMessage: (m: string | null) => void }) {
  const mine = d.myParticipation?.quantity ?? 0
  const [text, setText] = useState(mode === 'change' ? String(mine) : '')
  // 사용자가 화면에서 확인한 개당 가격. PRICE_CHANGED면 새 가격으로 바꾸고 다시 확인받는다.
  const [price, setPrice] = useState(d.unitPrice)
  const [needsReconfirm, setNeedsReconfirm] = useState(false)
  const join = useJoin(d.id)
  const change = useChangeQuantity(d.id)
  const m = mode === 'join' ? join : change

  const max = mode === 'join' ? d.remainingQuantity : d.remainingQuantity + mine
  const q = Number(text)
  const valid = text.trim() !== '' && Number.isInteger(q) && q >= 1 && q <= max
  const submitLabel = mode === 'join' ? '참여 신청' : '수량 변경 저장'

  const submit = () => {
    onMessage(null)
    m.mutate(
      { quantity: q, expectedUnitPrice: price },
      {
        onSuccess: onDone,
        onError: (e) => {
          const err = e instanceof ApiError ? e : new ApiError(0)
          if (err.code === 'PRICE_CHANGED' && typeof err.extra.currentUnitPrice === 'number') {
            setPrice(err.extra.currentUnitPrice)
            setNeedsReconfirm(true)
          }
          onMessage(errorMessage(err))
        },
      },
    )
  }

  return (
    <form noValidate style={{ flexBasis: '100%' }} onSubmit={(e) => { e.preventDefault(); if (valid && !needsReconfirm && !m.isPending) submit() }}>
      <TextField label="수량" type="number" inputMode="numeric" min={1} max={max} value={text} onChange={(e) => setText(e.target.value)} />
      {text.trim() !== '' && !valid && <p>{mode === 'join' ? `남은 수량(${max}개) 이하로 입력해 주세요.` : `최대 ${max}개까지 입력할 수 있어요.`}</p>}
      <p>개당 {formatWon(price)}</p>
      <p>{`내 부담 금액 ${valid ? formatWon(amount(price, q)) : '-'}`}</p>
      {needsReconfirm && <Button variant="secondary" onClick={() => setNeedsReconfirm(false)}>바뀐 가격 확인했어요</Button>}
      <Button type="submit" pending={m.isPending} disabled={!valid || needsReconfirm}>{submitLabel}</Button>
      <Button variant="secondary" disabled={m.isPending} onClick={onDone}>닫기</Button>
    </form>
  )
}
