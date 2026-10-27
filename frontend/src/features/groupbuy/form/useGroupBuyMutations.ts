import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cancelGroupBuy, createGroupBuy, updateGroupBuy } from '../api'
import type { GroupBuyRequest } from './fieldMap'

// 무효화 대상은 contracts/ui-api-map.md 5절.

export function useCreateGroupBuy() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (b: GroupBuyRequest) => createGroupBuy(b),
    onSuccess: () => Promise.all([qc.invalidateQueries({ queryKey: ['groupbuys'] }), qc.invalidateQueries({ queryKey: ['myGroupbuys'] })]),
  })
}

export function useUpdateGroupBuy(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (b: GroupBuyRequest) => updateGroupBuy(id, b),
    onSettled: () => qc.invalidateQueries({ queryKey: ['groupbuy', id] }), // 거부돼도 최신 상세를 다시 본다
  })
}

export function useCancelGroupBuy(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => cancelGroupBuy(id),
    onSuccess: () => {
      qc.removeQueries({ queryKey: ['groupbuy', id] }) // 취소된 공구는 404
      return Promise.all([qc.invalidateQueries({ queryKey: ['groupbuys'] }), qc.invalidateQueries({ queryKey: ['myGroupbuys'] })])
    },
    onError: () => qc.invalidateQueries({ queryKey: ['groupbuy', id] }),
  })
}
