import { describe, expect, test } from 'vitest'
import { normalizeQ, parseListParams, serializeListParams } from './listParams'

const parse = (s: string) => parseListParams(new URLSearchParams(s))

describe('listParams', () => {
  test('기본값: status=OPEN, sort=latest, 카테고리 전체, q 없음', () => {
    expect(parse('')).toEqual({ status: 'OPEN', category: null, q: '', sort: 'latest' })
  })

  test('유효한 값은 그대로 읽는다', () => {
    expect(parse('status=COMPLETED&category=FOOD&q=햇반&sort=deadline')).toEqual({
      status: 'COMPLETED',
      category: 'FOOD',
      q: '햇반',
      sort: 'deadline',
    })
  })

  test('알 수 없는 값은 기본값으로 되돌린다', () => {
    expect(parse('status=NOPE&category=XXX&sort=random')).toEqual({ status: 'OPEN', category: null, q: '', sort: 'latest' })
  })

  test('q는 앞뒤 공백을 제거하고, 공백뿐이면 검색 안 함, 50자로 자른다', () => {
    expect(normalizeQ('  햇반  ')).toBe('햇반')
    expect(normalizeQ('   ')).toBe('')
    expect(normalizeQ('a'.repeat(60))).toHaveLength(50)
    expect(parse('q=%20%20').q).toBe('')
  })

  test('커서는 주소에서 읽지도 쓰지도 않는다', () => {
    expect(parse('cursor=abc')).toEqual({ status: 'OPEN', category: null, q: '', sort: 'latest' })
    expect(serializeListParams({ status: 'OPEN', category: null, q: '', sort: 'latest' }).has('cursor')).toBe(false)
  })

  test('직렬화는 기본값을 생략하고 파싱과 왕복한다', () => {
    expect(serializeListParams({ status: 'OPEN', category: null, q: '', sort: 'latest' }).toString()).toBe('')
    const p = { status: 'FAILED', category: 'ETC', q: '물 티슈', sort: 'deadline' as const }
    expect(parseListParams(serializeListParams(p))).toEqual(p)
  })
})
