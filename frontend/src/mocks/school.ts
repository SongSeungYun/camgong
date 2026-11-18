// 009 가입 도메인 → 학교 키(plan 결정 1). 목의 추가 묶음은 고정 예시다(운영은 EXTRA_SCHOOL_DOMAINS).
const EXTRA = [['skku.edu', 'g.skku.edu']]
const AC_KR = /^(?:[a-z0-9-]+\.)*([a-z0-9-]+)\.ac\.kr$/

/** 받지 않는 도메인이면 null */
export function schoolOf(email: string): string | null {
  const domain = email.trim().toLowerCase().split('@')[1] ?? ''
  const group = EXTRA.find((g) => g.some((d) => domain === d || domain.endsWith(`.${d}`)))
  if (group) return group[0]!
  const m = AC_KR.exec(domain)
  return m ? `${m[1]}.ac.kr` : null
}

export const NOT_UNIVERSITY = '대학 메일로만 가입할 수 있습니다.'
