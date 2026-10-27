import { expect, test } from 'vitest'
import { amount, formatWon } from './money'

test('원 단위 포맷과 금액 계산', () => {
  expect(formatWon(1200)).toBe('1,200원')
  expect(formatWon(0)).toBe('0원')
  expect(amount(1200, 6)).toBe(7200)
})
