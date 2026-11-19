import { useInfiniteQuery } from '@tanstack/react-query'
import { fetchGroupBuys } from '../api'
import type { ListParams } from './listParams'

/**
 * 조건이 바뀌면 키가 달라져 커서를 버리고 처음부터 불러온다.
 * scope(009): 같은 조건이어도 비로그인(전국)과 로그인(우리 학교) 응답이 다르므로 키에 넣는다. null이면 로그인 확인 중이라 기다린다.
 */
export const useGroupBuyList = (params: ListParams, scope: string | null) =>
  useInfiniteQuery({
    queryKey: ['groupbuys', scope, params],
    queryFn: ({ pageParam }) => fetchGroupBuys(params, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: scope !== null,
  })
