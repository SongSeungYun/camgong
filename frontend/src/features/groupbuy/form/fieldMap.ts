import { toIsoWithKstOffset } from '../../../shared/lib/time'

/** 폼 필드 이름 = 001 계약 요청 본문 필드 이름(서버 errors[].field와 1:1). */
export const FORM_FIELDS = ['title', 'description', 'category', 'itemName', 'itemUrl', 'unitPrice', 'totalQuantity', 'organizerQuantity', 'deadline', 'openChatUrl'] as const

/** 입력칸 값은 모두 문자열이다. 숫자·오프셋 변환은 전송 직전에 한다. */
export type FormValues = Record<(typeof FORM_FIELDS)[number], string>

export const EMPTY_VALUES: FormValues = {
  title: '', description: '', category: '', itemName: '', itemUrl: '',
  unitPrice: '', totalQuantity: '', organizerQuantity: '', deadline: '', openChatUrl: '',
}

export interface GroupBuyRequest {
  title: string
  description?: string
  category: string
  itemName: string
  itemUrl: string
  unitPrice: number
  totalQuantity: number
  organizerQuantity: number
  deadline: string
  openChatUrl: string
}

/** 마감은 KST로 해석해 `+09:00` 오프셋 ISO로(FR-004). 빈 설명은 생략한다(수정에서는 지워진 것으로 본다). */
export function toRequestBody(v: FormValues): GroupBuyRequest {
  const description = v.description.trim()
  return {
    title: v.title.trim(),
    ...(description ? { description } : {}),
    category: v.category,
    itemName: v.itemName.trim(),
    itemUrl: v.itemUrl.trim(),
    unitPrice: Number(v.unitPrice),
    totalQuantity: Number(v.totalQuantity),
    organizerQuantity: Number(v.organizerQuantity),
    deadline: toIsoWithKstOffset(v.deadline),
    openChatUrl: v.openChatUrl.trim(),
  }
}
