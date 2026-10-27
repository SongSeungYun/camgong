import { SEED_MEMBERS, makeSeedGroupBuys, type SeedGroupBuy, type SeedMember } from './data/seed'

interface Pending {
  email: string
  displayName: string
  password: string
  token: string
  lastSentAt: number
}

/** 목 서버의 메모리 상태. 테스트는 reset()으로 초기화하고, 시나리오 스위치로 오류를 만든다. */
export const mockState = {
  members: [] as SeedMember[],
  groupBuys: [] as SeedGroupBuy[],
  pending: [] as Pending[],
  sessionMemberId: null as number | null,
  loginFailures: 0,
  nextMemberId: 4,
  nextToken: 1,
  /** 시나리오: true면 다음 인증 요청부터 세션이 만료된 것처럼 401 */
  sessionExpired: false,
  /** 시나리오: true면 로그인이 잠금(429) */
  loginLocked: false,
  reset() {
    this.members = SEED_MEMBERS.map((m) => ({ ...m }))
    this.groupBuys = makeSeedGroupBuys(Date.now())
    this.pending = []
    this.sessionMemberId = null
    this.loginFailures = 0
    this.nextMemberId = 4
    this.nextToken = 1
    this.sessionExpired = false
    this.loginLocked = false
  },
}
mockState.reset()

export const currentMember = () => {
  if (mockState.sessionExpired) mockState.sessionMemberId = null
  return mockState.members.find((m) => m.id === mockState.sessionMemberId) ?? null
}
