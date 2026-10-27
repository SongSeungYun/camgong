import { expect, test } from 'vitest'
import { externalLinkProps, safeHttpsUrl } from './safeLink'

test('https만 허용한다', () => {
  expect(safeHttpsUrl('https://open.kakao.com/o/abc')).toBe('https://open.kakao.com/o/abc')
  expect(safeHttpsUrl('http://example.com')).toBeNull()
  expect(safeHttpsUrl('javascript:alert(1)')).toBeNull()
  expect(safeHttpsUrl('data:text/html,x')).toBeNull()
  expect(safeHttpsUrl('not a url')).toBeNull()
  expect(safeHttpsUrl(null)).toBeNull()
  expect(safeHttpsUrl('')).toBeNull()
})

test('외부 링크 속성', () => {
  expect(externalLinkProps).toEqual({ target: '_blank', rel: 'noopener noreferrer' })
})
