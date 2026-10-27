import { del, post, put } from '../../shared/api/client'

export interface ParticipationBody {
  quantity: number
  /** 사용자가 화면에서 확인한 개당 가격. 현재 값과 다르면 서버가 PRICE_CHANGED로 거부한다 */
  expectedUnitPrice: number
}

export const join = (id: number, b: ParticipationBody) => post(`/api/groupbuys/${id}/participations`, b)
export const changeQuantity = (id: number, b: ParticipationBody) => put(`/api/groupbuys/${id}/participations/me`, b)
export const leave = (id: number) => del(`/api/groupbuys/${id}/participations/me`)
