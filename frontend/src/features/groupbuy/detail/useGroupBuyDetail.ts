import { useQuery } from '@tanstack/react-query'
import { get } from '../../../shared/api/client'
import type { GroupBuyDetail } from '../../../shared/api/types'

export const useGroupBuyDetail = (id: number) =>
  useQuery({ queryKey: ['groupbuy', id], queryFn: () => get<GroupBuyDetail>(`/api/groupbuys/${id}`) })
