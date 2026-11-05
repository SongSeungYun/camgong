import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useLocation } from 'react-router'
import { useSession } from '../auth/session'
import { fetchNotifications, fetchUnreadCount, markRead } from './api'

// 읽지 않은 수는 화면을 옮길 때마다 한 번 조회한다(폴링·포커스 재조회 없음, FR-035, research 3).
// 실패하면 배지만 숨기므로 재시도하지 않는다.
export function useUnreadCount() {
  const { status } = useSession()
  const q = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: fetchUnreadCount,
    enabled: status === 'authenticated',
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    retry: false,
  })
  const { pathname } = useLocation()
  const first = useRef(true)
  const { refetch } = q
  useEffect(() => {
    if (first.current) return void (first.current = false) // 첫 화면은 위 쿼리가 조회한다
    if (status === 'authenticated') void refetch()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 화면 이동에만 반응한다
  }, [pathname])
  return q
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
