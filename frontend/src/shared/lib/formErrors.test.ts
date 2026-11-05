import { expect, test } from 'vitest'
import { ApiError } from '../api/client'
import { applyServerErrors } from './formErrors'

// 001 계약: 같은 field에 오류가 둘 이상 올 수 있다. ui-api-map 3절: 모두 표시
test('같은 필드의 서버 오류는 모아서 한 번에 보여준다', () => {
  const calls: [string, string | undefined, boolean | undefined][] = []
  const err = new ApiError(400, undefined, undefined, [
    { field: 'title', message: '제목을 입력해 주세요' },
    { field: 'deadline', message: '마감은 30일 이내여야 해요' },
    { field: 'title', message: '제목은 100자 이하여야 해요' },
    { field: 'etc', message: '기타 오류' },
  ])
  const rest = applyServerErrors(err, (f, e, o) => void calls.push([f, e.message, o?.shouldFocus]), ['title', 'deadline'])
  expect(calls).toEqual([
    ['title', '제목을 입력해 주세요 / 제목은 100자 이하여야 해요', true],
    ['deadline', '마감은 30일 이내여야 해요', false],
  ])
  expect(rest).toEqual(['기타 오류'])
})
