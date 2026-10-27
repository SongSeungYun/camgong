import { useMutation, useQueryClient } from '@tanstack/react-query'
import { setConfirmed, type CheckKind } from './api'

// 성공하면 체크표를 필터와 무관하게 다시 불러온다(요약·필터 목록 포함, ui-api-map 5절).
// 거부(NOT_CHECKABLE 등)돼도 최신 체크표·상세를 다시 본다.
export function useSetConfirmed(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { memberId: number; kind: CheckKind; confirmed: boolean }) => setConfirmed(id, v.memberId, v.kind, v.confirmed),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['check', id] }),
    onError: () => qc.invalidateQueries({ queryKey: ['groupbuy', id] }),
  })
}
