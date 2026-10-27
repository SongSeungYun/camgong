import { readFileSync } from 'node:fs'
import { expect, test } from 'vitest'

// 색 대비 4.5:1(T062). tokens.css의 색 토큰을 읽어 글자/배경 조합을 계산한다.
const css = readFileSync('src/shared/ui/tokens.css', 'utf8') // vitest는 frontend/에서 실행된다
const color = (name: string) => css.match(new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`))![1]!
const lum = (hex: string) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!
}
const ratio = (a: string, b: string) => {
  const [hi, lo] = [lum(color(a)), lum(color(b))].sort((x, y) => y - x)
  return (hi! + 0.05) / (lo! + 0.05)
}

test.each([
  ['color-text', 'color-bg'],
  ['color-text', 'color-surface'],
  ['color-muted', 'color-bg'],
  ['color-muted', 'color-surface'],
  ['color-primary', 'color-bg'], // 링크
  ['color-primary-text', 'color-primary'], // 기본 버튼
  ['color-danger', 'color-bg'],
])('%s on %s ≥ 4.5', (fg, bg) => {
  expect(ratio(fg, bg)).toBeGreaterThanOrEqual(4.5)
})
