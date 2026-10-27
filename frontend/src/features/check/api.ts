import { get, put } from '../../shared/api/client'
import type { CheckRow, CheckSheet } from '../../shared/api/types'

export type CheckFilter = 'all' | 'unpaid' | 'unreceived'
export type CheckKind = 'payment' | 'receipt'

export const fetchCheckSheet = (id: number, filter: CheckFilter) =>
  get<CheckSheet>(`/api/groupbuys/${id}/check-sheet?filter=${filter}`)

/** 목표 상태를 보내는 멱등 호출. 응답은 갱신된 행. */
export const setConfirmed = (id: number, memberId: number, kind: CheckKind, confirmed: boolean) =>
  put<CheckRow>(`/api/groupbuys/${id}/participations/${memberId}/${kind}`, { confirmed })
