import { post } from '../../shared/api/client'
import type { Member } from '../../shared/api/types'

/** 변경 B2: 가입 신청은 이메일만. 이름·비밀번호·동의는 인증 화면에서 받는다. */
export interface VerifyBody {
  token: string
  displayName: string
  password: string
  consent: boolean
  consentVersion: string
}
export interface Accepted {
  email: string
  resendAvailableAt: string
}

export const signup = (email: string) => post<Accepted>('/api/auth/signup', { email })
export const resend = (email: string) => post<Accepted>('/api/auth/signup/resend', { email })
export const login = (email: string, password: string) => post<{ member: Member }>('/api/auth/login', { email, password })
export const verify = (b: VerifyBody) => post<{ member: Member }>('/api/auth/verify', b)

/** 010 탈퇴를 막는 진행 중 공구(409 ACTIVE_GROUPBUY의 blockers) */
export interface WithdrawBlocker {
  groupBuyId: number
  title: string
  role: 'ORGANIZER' | 'PARTICIPANT' | string
  status: 'OPEN' | 'PENDING_CLOSE' | string
}
export const withdraw = (password: string) => post('/api/auth/withdraw', { password })
