import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import type { GroupBuyDetail } from '../../../shared/api/types'
import { errorMessage } from '../../../shared/lib/errorMessage'
import { Button, ConfirmDialog } from '../../../shared/ui'
import { useCancelGroupBuy } from '../form/useGroupBuyMutations'
import s from './GroupBuyDetail.module.css'

/** 개설자 영역: 수정 이동과 공구 취소(확인 대화상자를 거친 뒤에만 실행, FR-026). */
export function OrganizerActions({ d }: { d: GroupBuyDetail }) {
  const [asking, setAsking] = useState(false)
  const cancel = useCancelGroupBuy(d.id)
  const navigate = useNavigate()
  const err = cancel.error instanceof ApiError ? cancel.error : cancel.error ? new ApiError(0) : null

  return (
    <>
      <Link className={s.linkButton} to={`/groupbuys/${d.id}/edit`}>수정</Link>
      <Button variant="secondary" onClick={() => setAsking(true)}>공구 취소</Button>
      {err && <p role="alert">{errorMessage(err)}</p>}
      <ConfirmDialog
        open={asking}
        title="공구를 취소할까요?"
        message="취소하면 되돌릴 수 없어요."
        confirmLabel="공구 취소하기"
        cancelLabel="돌아가기"
        pending={cancel.isPending}
        onCancel={() => setAsking(false)}
        onConfirm={() =>
          cancel.mutate(undefined, {
            onSuccess: () => navigate('/', { state: { notice: '공구를 취소했어요. 목록에서 사라졌어요.' } }),
            onSettled: () => setAsking(false),
          })
        }
      />
    </>
  )
}
