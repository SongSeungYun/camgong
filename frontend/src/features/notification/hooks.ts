import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSession } from '../auth/session'
import { fetchNotifications, fetchUnreadCount, markRead } from './api'

// 읽지 않은 수는 로그인된 화면을 불러올 때 한 번만 조회한다(폴링·포커스 재조회 없음, FR-035).
// 실패하면 배지만 숨기므로 재시도하지 않는다.
export function useUnreadCount() {
  const { status } = useSession()
  return useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: fetchUnreadCount,
    enabled: status === 'authenticated',
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    retry: false,
  })
}

export const useNotifications = () =>
  useInfiniteQuery({
    queryKey: ['notifications', 'list'],
    queryFn: ({ pageParam }) => fetchNotifications(pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })

// 읽음 처리 성공 → 알림함·읽지 않은 수를 다시 불러온다(ui-api-map 5절)
export function useMarkRead() {
  const qc = useQueryClient()
  return useMutation({ mutationFn: markRead, onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }) })
}
