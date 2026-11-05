/** 주소의 공구 번호. 1 이상의 안전한 정수가 아니면 NaN(요청하지 않고 '찾을 수 없음'). 2^53을 넘으면 다른 번호로 반올림되므로 막는다(FR-023). */
export const routeId = (raw: string | undefined): number => {
  const n = raw && /^\d+$/.test(raw) ? Number(raw) : NaN
  return Number.isSafeInteger(n) && n >= 1 ? n : NaN
}
