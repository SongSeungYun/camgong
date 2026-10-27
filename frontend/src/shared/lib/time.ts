const kst = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** ISO 시각을 기기 시간대와 무관하게 한국 시각 `2026-11-01 18:00`으로 표시한다. */
export function formatKst(iso: string): string {
  const ms = Date.parse(iso)
  if (Number.isNaN(ms)) return ''
  const p = Object.fromEntries(kst.formatToParts(ms).map((x) => [x.type, x.value]))
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}`
}

/** `datetime-local` 값(`2026-11-01T18:00`)을 KST로 해석해 `+09:00` 오프셋 ISO 문자열로 바꾼다. */
export function toIsoWithKstOffset(local: string): string {
  const m = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})(?::(\d{2}))?$/.exec(local)
  if (!m) throw new RangeError(`datetime-local 형식이 아님: ${local}`)
  return `${m[1]}:${m[2] ?? '00'}+09:00`
}

/** 불러온 시점(nowMs) 기준 남은 시간. */
export function remainingText(deadlineIso: string, nowMs: number): string {
  const diff = Date.parse(deadlineIso) - nowMs
  if (Number.isNaN(diff)) return ''
  if (diff <= 0) return '마감됨'
  const min = Math.floor(diff / 60_000)
  if (min < 1) return '1분 미만 남음'
  if (min < 60) return `${min}분 남음`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour}시간 남음`
  return `${Math.floor(hour / 24)}일 남음`
}
