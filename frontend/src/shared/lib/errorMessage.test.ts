import { describe, expect, test } from 'vitest'
import { ApiError } from '../api/client'
import { errorMessage } from './errorMessage'

const e = (status: number, code?: string, reason?: string, extra: Record<string, unknown> = {}) =>
  new ApiError(status, code, reason, [], extra)
const now = Date.parse('2026-11-01T00:00:00+09:00')

// ui-api-map.md 3절 표의 행마다 한 줄
describe('errorMessage', () => {
  test('400 필드 오류', () => expect(errorMessage(e(400))).toBe('입력한 내용을 확인해 주세요.'))
  test('401 UNAUTHENTICATED', () => expect(errorMessage(e(401, 'UNAUTHENTICATED'))).toBe('로그인이 필요해요.'))
  test('401 폼에서 발생하면 소실 안내', () =>
    expect(errorMessage(e(401, 'UNAUTHENTICATED'), { fromForm: true })).toBe('로그인이 필요해요. 입력하던 내용은 저장되지 않아요.'))
  test('401 INVALID_CREDENTIALS는 어느 쪽인지 알리지 않음', () =>
    expect(errorMessage(e(401, 'INVALID_CREDENTIALS'))).toBe('이메일 또는 비밀번호가 맞지 않아요.'))
  test('403 ORGANIZER_NOT_ALLOWED', () =>
    expect(errorMessage(e(403, 'ORGANIZER_NOT_ALLOWED'))).toBe('개설자는 직접 참여할 수 없어요. 수량은 공구 수정에서 바꿔요.'))
  test('403 ORGANIZER_ONLY', () => expect(errorMessage(e(403, 'ORGANIZER_ONLY'))).toBe('개설자만 쓸 수 있어요.'))
  test('404 공구 없음', () => expect(errorMessage(e(404))).toBe('찾을 수 없는 공구예요.'))
  test('404 PARTICIPATION_NOT_FOUND', () => expect(errorMessage(e(404, 'PARTICIPATION_NOT_FOUND'))).toBe('참여 내역이 없어요.'))
  test('409 NOT_JOINABLE/DEADLINE_PASSED', () =>
    expect(errorMessage(e(409, 'NOT_JOINABLE', 'DEADLINE_PASSED'))).toBe('마감 시간이 지나서 참여할 수 없어요.'))
  test('409 NOT_JOINABLE/COMPLETED', () =>
    expect(errorMessage(e(409, 'NOT_JOINABLE', 'COMPLETED'))).toBe('이미 모집이 끝난 공구예요.'))
  test('409 NOT_JOINABLE/FAILED', () => expect(errorMessage(e(409, 'NOT_JOINABLE', 'FAILED'))).toBe('모집에 실패한 공구예요.'))
  test('409 NOT_JOINABLE 알 수 없는 reason', () =>
    expect(errorMessage(e(409, 'NOT_JOINABLE', 'NEW'))).toBe('지금은 참여할 수 없는 공구예요.'))
  test('409 NOT_MODIFIABLE (두 reason 같은 문구)', () => {
    const m = '이미 모집이 끝났거나 마감이 지나서 수정·취소할 수 없어요.'
    expect(errorMessage(e(409, 'NOT_MODIFIABLE', 'COMPLETED'))).toBe(m)
    expect(errorMessage(e(409, 'NOT_MODIFIABLE', 'DEADLINE_PASSED'))).toBe(m)
  })
  test('409 INSUFFICIENT_QUANTITY + remainingQuantity', () =>
    expect(errorMessage(e(409, 'INSUFFICIENT_QUANTITY', undefined, { remainingQuantity: 3 }))).toBe('남은 수량이 부족해요. 지금 남은 수량은 3개예요.'))
  test('409 PRICE_CHANGED + currentUnitPrice', () =>
    expect(errorMessage(e(409, 'PRICE_CHANGED', undefined, { currentUnitPrice: 1300 }))).toBe('개당 가격이 바뀌었어요(1,300원). 금액을 다시 확인해 주세요.'))
  test('409 ALREADY_JOINED', () => expect(errorMessage(e(409, 'ALREADY_JOINED'))).toBe("이미 참여했어요. 수량은 '수량 변경'을 써요."))
  test('409 BUSY', () => expect(errorMessage(e(409, 'BUSY'))).toBe('요청이 몰렸어요. 잠시 뒤 다시 시도해 주세요.'))
  test('409 DUPLICATE_REQUEST', () => expect(errorMessage(e(409, 'DUPLICATE_REQUEST'))).toBe('같은 공구가 이미 열려 있어요.'))
  test('409 NOT_CHECKABLE', () => expect(errorMessage(e(409, 'NOT_CHECKABLE'))).toBe('모집이 완료된 공구에서만 체크할 수 있어요.'))
  test('409 ALREADY_REGISTERED', () => expect(errorMessage(e(409, 'ALREADY_REGISTERED'))).toBe('이미 가입된 이메일이에요. 로그인해 주세요.'))
  test('409 DISPLAY_NAME_TAKEN', () =>
    expect(errorMessage(e(409, 'DISPLAY_NAME_TAKEN'))).toBe('이미 쓰이는 이름이에요. 다른 이름을 입력해 주세요.'))
  test('409 TOKEN_USED', () => expect(errorMessage(e(409, 'TOKEN_USED'))).toBe('이미 쓴 링크예요.'))
  test('410 TOKEN_EXPIRED', () => expect(errorMessage(e(410, 'TOKEN_EXPIRED'))).toBe('링크가 만료됐어요.'))
  test('400 INVALID_TOKEN', () => expect(errorMessage(e(400, 'INVALID_TOKEN'))).toBe('올바르지 않은 링크예요.'))
  test('429 TOO_MANY_REQUESTS + retryAt', () =>
    expect(errorMessage(e(429, 'TOO_MANY_REQUESTS', undefined, { retryAt: '2026-11-01T00:05:00+09:00' }), { nowMs: now })).toBe(
      '잠시 뒤에 다시 시도해 주세요. (5분 남음)',
    ))
  test('429 LOGIN_LOCKED + retryAt', () =>
    expect(errorMessage(e(429, 'LOGIN_LOCKED', undefined, { retryAt: '2026-11-01T00:15:00+09:00' }), { nowMs: now })).toBe(
      '로그인 시도가 너무 많아요. 잠시 뒤 다시 시도해 주세요. (15분 남음)',
    ))
  test('429 retryAt 없으면 남은 시간 생략', () =>
    expect(errorMessage(e(429, 'TOO_MANY_REQUESTS'))).toBe('잠시 뒤에 다시 시도해 주세요.'))
  test('5xx·네트워크는 일반 문구', () => {
    const g = '잠시 문제가 생겼어요. 다시 시도해 주세요.'
    expect(errorMessage(e(500))).toBe(g)
    expect(errorMessage(e(0))).toBe(g)
  })
  test('알 수 없는 코드·상태는 일반 문구', () => {
    const g = '잠시 문제가 생겼어요. 다시 시도해 주세요.'
    expect(errorMessage(e(409, 'BRAND_NEW'))).toBe(g)
    expect(errorMessage(e(418))).toBe(g)
  })
})
