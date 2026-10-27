import { post } from '../../shared/api/client'
import type { Member } from '../../shared/api/types'

export interface SignupBody {
  email: string
  displayName: string
  password: string
  consent: boolean
  consentVersion: string
}
export interface Accepted {
  email: string
  resendAvailableAt: string
}

export const signup = (b: SignupBody) => post<Accepted>('/api/auth/signup', b)
export const resend = (email: string) => post<Accepted>('/api/auth/signup/resend', { email })
export const login = (email: string, password: string) => post<{ member: Member }>('/api/auth/login', { email, password })
export const verify = (token: string, displayName?: string) => post<{ member: Member }>('/api/auth/verify', displayName ? { token, displayName } : { token })
