import { describe, expect, test, vi } from 'vitest'
import { ApiError } from '../../../shared/api/client'
import { applyServerErrors } from '../../../shared/lib/formErrors'
import { FORM_FIELDS, toRequestBody, type FormValues } from './fieldMap'

// 001 계약 '공통 요청 본문' 필드
const CONTRACT_FIELDS = ['title', 'description', 'itemName', 'unitPrice', 'totalQuantity', 'deadline', 'organizerQuantity', 'category', 'itemUrl', 'openChatUrl']

const values: FormValues = {
  title: ' 햇반 ',
  description: '',
  category: 'FOOD',
  itemName: '햇반 210g',
  itemUrl: 'https://example.com/a',
  unitPrice: '1200',
  totalQuantity: '24',
  organizerQuantity: '9',
  deadline: '2026-11-01T18:00',
  openChatUrl: 'https://open.kakao.com/o/x',
}

describe('폼 필드 ↔ 계약 요청 본문', () => {
  test('폼 필드 이름은 계약 필드와 1:1', () => {
    expect([...FORM_FIELDS].sort()).toEqual([...CONTRACT_FIELDS].sort())
  })

  test('요청 본문 키도 계약 필드(빈 설명은 생략), 숫자는 number, 마감은 +09:00 오프셋', () => {
    const body = toRequestBody(values)
    expect(Object.keys(body).sort()).toEqual(CONTRACT_FIELDS.filter((f) => f !== 'description').sort())
    expect(body).toMatchObject({ title: '햇반', unitPrice: 1200, totalQuantity: 24, organizerQuantity: 9, deadline: '2026-11-01T18:00:00+09:00' })
    expect(toRequestBody({ ...values, description: '메모' })).toMatchObject({ description: '메모' })
  })

  test('서버 errors[].field가 모두 폼 필드에 붙거나 폼 상단으로 모인다', () => {
    const setError = vi.fn()
    const err = new ApiError(400, 'Bad Request', undefined, [
      ...FORM_FIELDS.map((field) => ({ field, message: `${field} 오류` })),
      { field: 'memberId', message: '폼에 없는 필드' },
    ])
    const top = applyServerErrors(err, setError, FORM_FIELDS)
    expect(setError).toHaveBeenCalledTimes(FORM_FIELDS.length)
    expect(FORM_FIELDS.every((f) => setError.mock.calls.some((c) => c[0] === f))).toBe(true)
    expect(top).toEqual(['폼에 없는 필드'])
  })
})
