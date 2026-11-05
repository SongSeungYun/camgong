import { beforeEach, expect, test } from 'vitest'
import { ApiError, put } from '../shared/api/client'
import { seedGroupBuy } from '../test/factories'
import { mockState } from './state'

beforeEach(() => mockState.reset())

const err = (p: Promise<unknown>) => p.then(() => { throw new Error('거부될 것으로 기대했지만 성공했다') }, (e: ApiError) => e)

// 006 계약: 본문 검증(400)이 접근 검사(404/403)보다 먼저, 코드는 INVALID_TARGET·PARTICIPATION_NOT_FOUND
test('목 체크 PUT이 계약 순서와 코드로 거부한다', async () => {
  seedGroupBuy({ status: 'COMPLETED', participations: [{ memberId: 1, quantity: 1 }, { memberId: 2, quantity: 1 }] })
  const url = (m: number, g = 1) => `/api/groupbuys/${g}/participations/${m}/payment`
  mockState.sessionMemberId = 2 // 개설자가 아님
  expect(await err(put(url(2), {}))).toMatchObject({ status: 400, fieldErrors: [{ field: 'confirmed' }] })
  expect(await err(put(url(2, 999), {}))).toMatchObject({ status: 400 })
  expect(await err(put(url(2), { confirmed: true }))).toMatchObject({ status: 403, code: 'ORGANIZER_ONLY' })
  mockState.sessionMemberId = 1
  expect(await err(put(url(1), { confirmed: true }))).toMatchObject({ status: 400, code: 'INVALID_TARGET', fieldErrors: [{ field: 'memberId' }] })
  expect(await err(put(url(3), { confirmed: true }))).toMatchObject({ status: 404, code: 'PARTICIPATION_NOT_FOUND' })
})
