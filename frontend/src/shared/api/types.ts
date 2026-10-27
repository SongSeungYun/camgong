// 서버 계약(specs/001~007/contracts)에서 옮겨 적은 타입. 서버가 기준이다.

export const STATUSES = ['OPEN', 'PENDING_CLOSE', 'COMPLETED', 'FAILED'] as const
export type KnownStatus = (typeof STATUSES)[number]
export const isKnownStatus = (s: string): s is KnownStatus => (STATUSES as readonly string[]).includes(s)

export const CATEGORY_LABELS = {
  FOOD: '식품',
  DAILY: '생활용품',
  KITCHEN: '주방',
  HEALTH_BEAUTY: '건강뷰티',
  STATIONERY: '문구학용품',
  ETC: '기타',
} as const
export type KnownCategory = keyof typeof CATEGORY_LABELS
export const isKnownCategory = (c: string): c is KnownCategory => c in CATEGORY_LABELS
/** 알 수 없는 카테고리는 값을 그대로 보여 화면이 멈추지 않게 한다. */
export const categoryLabel = (c: string): string => (isKnownCategory(c) ? CATEGORY_LABELS[c] : c)

export const NOTIFICATION_TYPES = ['GROUPBUY_COMPLETED', 'GROUPBUY_FAILED', 'GROUPBUY_CHANGED', 'GROUPBUY_CANCELLED'] as const
export type KnownNotificationType = (typeof NOTIFICATION_TYPES)[number]
export const isKnownNotificationType = (t: string): t is KnownNotificationType =>
  (NOTIFICATION_TYPES as readonly string[]).includes(t)

// status·category·type은 서버가 새 값을 줄 수 있어 string으로 받고 위 가드로 좁힌다.

export interface Member {
  id: number
  displayName: string
}

export interface Page<T> {
  items: T[]
  nextCursor: string | null
}

export interface GroupBuyCard {
  id: number
  title: string
  itemName: string
  category: string
  unitPrice: number
  securedQuantity: number
  totalQuantity: number
  deadline: string
  status: string
  /** 내 활동 role=participant일 때만 */
  myQuantity?: number
}

export interface MyParticipation {
  quantity: number
  amount: number
  /** 006 확장: 개설자의 myParticipation에는 없다 */
  paidConfirmedAt?: string | null
  receivedConfirmedAt?: string | null
}

export interface ParticipantSummary {
  memberId: number
  name: string
  quantity: number
}

export interface GroupBuyDetail {
  id: number
  title: string
  description: string | null
  itemName: string
  category: string
  itemUrl: string | null
  unitPrice: number
  totalQuantity: number
  securedQuantity: number
  remainingQuantity: number
  deadline: string
  status: string
  organizerName: string
  isOrganizer: boolean
  participantCount: number
  myParticipation: MyParticipation | null
  openChatUrl: string | null
  participants: ParticipantSummary[] | null
  createdAt: string
}

export interface CheckRow {
  memberId: number
  name: string
  quantity: number
  amount: number
  paidConfirmedAt: string | null
  receivedConfirmedAt: string | null
  receivedWithoutPayment: boolean
}

export interface CheckSheet {
  summary: {
    participantCount: number
    paidCount: number
    receivedCount: number
    amountDue: number
    amountConfirmed: number
  }
  rows: CheckRow[]
}

export interface AppNotification {
  id: number
  type: string
  groupBuyId: number
  groupBuyTitle: string
  changedFields: string[] | null
  createdAt: string
  read: boolean
  linkable: boolean
}
