import { get, post, put } from '../../shared/api/client'
import type { GroupBuyCard, Page } from '../../shared/api/types'
import type { GroupBuyRequest } from './form/fieldMap'
import type { ListParams } from './list/listParams'

export function fetchGroupBuys(p: ListParams, cursor?: string): Promise<Page<GroupBuyCard>> {
  const sp = new URLSearchParams({ status: p.status, sort: p.sort })
  if (p.category) sp.set('category', p.category)
  if (p.q) sp.set('q', p.q)
  if (cursor) sp.set('cursor', cursor)
  return get(`/api/groupbuys?${sp}`)
}

export const createGroupBuy = (b: GroupBuyRequest) => post<{ id: number }>('/api/groupbuys', b)
export const updateGroupBuy = (id: number, b: GroupBuyRequest) => put<{ id: number }>(`/api/groupbuys/${id}`, b)
export const cancelGroupBuy = (id: number) => post(`/api/groupbuys/${id}/cancel`)
