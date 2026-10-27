import { useInfiniteQuery } from '@tanstack/react-query'
import { fetchMyGroupBuys, type MyRole } from '../api'

// 키 접두사 'myGroupbuys'는 개설·참여·취소 성공 시 무효화 대상이다(ui-api-map 5절).
export const useMyGroupBuys = (role: MyRole) =>
  useInfiniteQuery({
    queryKey: ['myGroupbuys', role],
    queryFn: ({ pageParam }) => fetchMyGroupBuys(role, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
