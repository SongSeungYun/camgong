// 목 데이터. 모양은 specs/003 계약과 같다. 회원 3명: 개설자·참여자·비참여자 역할을 시험한다.

export interface SeedMember {
  id: number
  displayName: string
  email: string
  password: string // 목 전용 가짜 값. 실제 계정이 아니다.
}

export const SEED_MEMBERS: SeedMember[] = [
  { id: 1, displayName: '개설자', email: 'organizer@school.ac.kr', password: 'password1234' },
  { id: 2, displayName: '참여자', email: 'participant@school.ac.kr', password: 'password1234' },
  { id: 3, displayName: '구경꾼', email: 'outsider@school.ac.kr', password: 'password1234' },
]

export interface SeedGroupBuy {
  id: number
  title: string
  description: string | null
  itemName: string
  category: string
  itemUrl: string | null
  unitPrice: number
  totalQuantity: number
  deadline: string
  status: string
  organizerId: number
  createdAt: string
  openChatUrl: string | null
  /** 개설자 본인 몫 포함 */
  participations: { memberId: number; quantity: number; paidConfirmedAt?: string | null; receivedConfirmedAt?: string | null }[]
}

const CATEGORIES = ['FOOD', 'DAILY', 'KITCHEN', 'HEALTH_BEAUTY', 'STATIONERY', 'ETC']
const STATUSES = ['OPEN', 'OPEN', 'OPEN', 'PENDING_CLOSE', 'COMPLETED', 'FAILED']
const NAMES = ['햇반', '물티슈', '텀블러', '비타민', '노트', '라면', '세제', '프라이팬']
const HOUR = 3_600_000

/** 50개(모집 중 25개 이상): 상태 4종·카테고리 6종·마감 임박(24시간 이내) 포함. 기준 시각 now에서 상대적으로 만든다. */
export function makeSeedGroupBuys(now: number): SeedGroupBuy[] {
  const iso = (ms: number) => new Date(Math.floor(ms / 60_000) * 60_000).toISOString() // 분 단위: 마감 입력(datetime-local)과 같은 정밀도
  return Array.from({ length: 50 }, (_, k) => {
    const id = k + 1
    const status = STATUSES[id % STATUSES.length]!
    const total = 10 + (id % 5) * 5
    const organizerId = (id % 3) + 1
    const organizerQty = 1 + (id % 3)
    const participations = [{ memberId: organizerId, quantity: organizerQty }]
    if (status !== 'OPEN' || id % 2 === 0) {
      const other = organizerId === 2 ? 3 : 2
      participations.push({ memberId: other, quantity: status === 'COMPLETED' ? total - organizerQty : 2 })
    }
    const name = NAMES[id % NAMES.length]!
    const open = status === 'OPEN'
    return {
      id,
      title: `${name} ${total}개 공구`,
      description: id % 4 === 0 ? null : `${name} 같이 사요. 기숙사 로비에서 나눠요.`,
      itemName: `${name} 세트`,
      category: CATEGORIES[(id + Math.floor(id / 6)) % CATEGORIES.length]!,
      itemUrl: id % 5 === 0 ? 'http://insecure.example.com/item' : `https://example.com/item/${id}`,
      unitPrice: 1000 + id * 100,
      totalQuantity: total,
      // 앞의 두 OPEN은 마감 임박, 나머지는 며칠 뒤. 닫힌 공구는 과거.
      deadline: iso(now + (open ? (id <= 2 ? 5 * HOUR : id * 24 * HOUR) : -id * HOUR)),
      status,
      organizerId,
      createdAt: iso(now - (51 - id) * HOUR),
      openChatUrl: 'https://open.kakao.com/o/mock' + id, // 노출 여부는 핸들러가 역할·상태로 정한다
      participations,
    }
  })
}
