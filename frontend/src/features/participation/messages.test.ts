import { expect, test } from 'vitest'
import { ApiError } from '../../shared/api/client'
import { errorMessage } from '../../shared/lib/errorMessage'

const e = (status: number, code?: string, reason?: string, extra: Record<string, unknown> = {}) => new ApiError(status, code, reason, [], extra)

// ui-api-map.md 3절의 참여 관련 행: 거부 사유마다 서로 다른 문구여야 한다(SC-007)
test('참여 거부 사유 9종이 모두 서로 다른 문구를 낸다', () => {
  const msgs = [
    errorMessage(e(409, 'NOT_JOINABLE', 'DEADLINE_PASSED')),
    errorMessage(e(409, 'NOT_JOINABLE', 'COMPLETED')),
    errorMessage(e(409, 'NOT_JOINABLE', 'FAILED')),
    errorMessage(e(409, 'INSUFFICIENT_QUANTITY', undefined, { remainingQuantity: 3 })),
    errorMessage(e(409, 'PRICE_CHANGED', undefined, { currentUnitPrice: 1500 })),
    errorMessage(e(409, 'ALREADY_JOINED')),
    errorMessage(e(409, 'BUSY')),
    errorMessage(e(403, 'ORGANIZER_NOT_ALLOWED')),
    errorMessage(e(404, 'PARTICIPATION_NOT_FOUND')),
  ]
  expect(new Set(msgs).size).toBe(msgs.length)
  expect(errorMessage(e(404))).not.toBe(msgs[8]) // 공구 없음과 참여 내역 없음도 구분
})

test('수치가 문구에 반영된다', () => {
  expect(errorMessage(e(409, 'INSUFFICIENT_QUANTITY', undefined, { remainingQuantity: 3 }))).toContain('3개')
  expect(errorMessage(e(409, 'PRICE_CHANGED', undefined, { currentUnitPrice: 1500 }))).toContain('1,500원')
})
