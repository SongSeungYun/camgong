import { useInfiniteQuery } from '@tanstack/react-query'
import { fetchGroupBuys } from '../api'
import type { ListParams } from './listParams'

/** 조건이 바뀌면 키가 달라져 커서를 버리고 처음부터 불러온다. */
export const useGroupBuyList = (params: ListParams) =>
  useInfiniteQuery({
    queryKey: ['groupbuys', params],
    queryFn: ({ pageParam }) => fetchGroupBuys(params, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
