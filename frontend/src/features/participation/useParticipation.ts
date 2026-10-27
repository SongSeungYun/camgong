import { useMutation, useQueryClient } from '@tanstack/react-query'
import { changeQuantity, join, leave, type ParticipationBody } from './api'

// 성공하면 상세·목록·내 활동을 다시 불러온다(ui-api-map 5절, FR-030). 거부돼도 최신 상세를 다시 본다(FR-028).
// onSuccess가 refetch를 기다리므로 최신 수치가 올 때까지 mutation은 pending이고 버튼은 비활성이다.
function useRefreshing<V, R>(id: number, fn: (v: V) => Promise<R>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () =>
      Promise.all([
        qc.invalidateQueries({ queryKey: ['groupbuy', id] }),
        qc.invalidateQueries({ queryKey: ['groupbuys'] }),
        qc.invalidateQueries({ queryKey: ['myGroupbuys'] }),
      ]),
    onError: () => qc.invalidateQueries({ queryKey: ['groupbuy', id] }),
  })
}

export const useJoin = (id: number) => useRefreshing(id, (b: ParticipationBody) => join(id, b))
export const useChangeQuantity = (id: number) => useRefreshing(id, (b: ParticipationBody) => changeQuantity(id, b))
export const useLeave = (id: number) => useRefreshing(id, () => leave(id))
