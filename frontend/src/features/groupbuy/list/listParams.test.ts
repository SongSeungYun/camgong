import { describe, expect, test } from 'vitest'
import { normalizeQ, parseListParams, serializeListParams } from './listParams'

const parse = (s: string) => parseListParams(new URLSearchParams(s))

describe('listParams', () => {
  test('기본값: status=OPEN, sort=latest, 카테고리 전체, q 없음', () => {
    expect(parse('')).toEqual({ status: 'OPEN', category: null, q: '', sort: 'latest', school: '' })
  })

  test('유효한 값은 그대로 읽는다', () => {
    expect(parse('status=COMPLETED&category=FOOD&q=햇반&sort=deadline')).toEqual({
      status: 'COMPLETED',
      category: 'FOOD',
      q: '햇반',
      sort: 'deadline',
      school: '',
    })
  })

  test('알 수 없는 값은 기본값으로 되돌린다', () => {
    expect(parse('status=NOPE&category=XXX&sort=random')).toEqual({ status: 'OPEN', category: null, q: '', sort: 'latest', school: '' })
  })

  test('q는 앞뒤 공백을 제거하고, 공백뿐이면 검색 안 함, 50자로 자른다', () => {
    expect(normalizeQ('  햇반  ')).toBe('햇반')
    expect(normalizeQ('   ')).toBe('')
    expect(normalizeQ('a'.repeat(60))).toHaveLength(50)
    expect(parse('q=%20%20').q).toBe('')
  })

  test('커서는 주소에서 읽지도 쓰지도 않는다', () => {
    expect(parse('cursor=abc')).toEqual({ status: 'OPEN', category: null, q: '', sort: 'latest', school: '' })
    expect(serializeListParams({ status: 'OPEN', category: null, q: '', sort: 'latest', school: '' }).has('cursor')).toBe(false)
  })

  test('직렬화는 기본값을 생략하고 파싱과 왕복한다', () => {
    expect(serializeListParams({ status: 'OPEN', category: null, q: '', sort: 'latest', school: '' }).toString()).toBe('')
    const p = { status: 'FAILED', category: 'ETC', q: '물 티슈', sort: 'deadline' as const, school: 'snu.ac.kr' }
    expect(parseListParams(serializeListParams(p))).toEqual(p)
  })
  test('검색어 50자는 글자(code point) 수로 자른다: 이모지가 반쪽으로 잘리지 않는다', () => {
    expect(normalizeQ('😀'.repeat(60))).toBe('😀'.repeat(50))
    expect(normalizeQ('가'.repeat(49) + '😀😀')).toBe('가'.repeat(49) + '😀')
  })
  test('009: school은 앞뒤 공백을 지운 학교 키, 253자 초과면 무시', () => {
    expect(parse('school=%20snu.ac.kr%20').school).toBe('snu.ac.kr')
    expect(parse(`school=${'a'.repeat(254)}`).school).toBe('')
    expect(serializeListParams({ status: 'OPEN', category: null, q: '', sort: 'latest', school: 'snu.ac.kr' }).toString()).toBe('school=snu.ac.kr')
  })
})
