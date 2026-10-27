import { describe, expect, test } from 'vitest'
import { formatKst, remainingText, toIsoWithKstOffset } from './time'

describe('time (테스트는 TZ=UTC로 실행: npm test)', () => {
  test('기기 시간대가 UTC인지 확인한다(아래 테스트가 의미를 가지려면 필요)', () => {
    expect(new Date(2026, 0, 1).getTimezoneOffset()).toBe(0)
  })

  test('formatKst는 기기 시간대와 무관하게 한국 시각을 보여준다', () => {
    expect(formatKst('2026-11-01T09:00:00Z')).toBe('2026-11-01 18:00')
    expect(formatKst('2026-11-01T18:00:00+09:00')).toBe('2026-11-01 18:00')
    expect(formatKst('2026-10-31T15:00:00Z')).toBe('2026-11-01 00:00')
  })

  test('formatKst는 잘못된 값에 빈 문자열', () => {
    expect(formatKst('nope')).toBe('')
  })

  test('toIsoWithKstOffset은 +09:00 오프셋을 붙인다', () => {
    expect(toIsoWithKstOffset('2026-11-01T18:00')).toBe('2026-11-01T18:00:00+09:00')
    expect(toIsoWithKstOffset('2026-11-01T18:00:30')).toBe('2026-11-01T18:00:30+09:00')
    expect(Date.parse(toIsoWithKstOffset('2026-11-01T18:00'))).toBe(Date.parse('2026-11-01T09:00:00Z'))
  })

  test('toIsoWithKstOffset은 형식이 틀리면 던진다', () => {
    expect(() => toIsoWithKstOffset('2026-11-01')).toThrow(RangeError)
  })

  test('remainingText', () => {
    const now = Date.parse('2026-11-01T00:00:00+09:00')
    const at = (ms: number) => new Date(now + ms).toISOString()
    expect(remainingText(at(-1), now)).toBe('마감됨')
    expect(remainingText(at(30_000), now)).toBe('1분 미만 남음')
    expect(remainingText(at(5 * 60_000), now)).toBe('5분 남음')
    expect(remainingText(at(3 * 3_600_000), now)).toBe('3시간 남음')
    expect(remainingText(at(2 * 86_400_000 + 1), now)).toBe('2일 남음')
  })
})
