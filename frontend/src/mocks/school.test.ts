import { beforeEach, expect, test } from 'vitest'
import { ApiError, del, get, post, put } from '../shared/api/client'
import type { GroupBuyCard, Page } from '../shared/api/types'
import { makeGroupBuy } from '../test/factories'
import { mockState } from './state'

// 009 계약(specs/009-school-scope/contracts/school-api.md)대로 목이 응답하는지
beforeEach(() => mockState.reset())

const err = (p: Promise<unknown>) => p.then(() => { throw new Error('거부될 것으로 기대했지만 성공했다') }, (e: ApiError) => e)
const list = (qs = '') => get<Page<GroupBuyCard>>(`/api/groupbuys${qs}`)
const v = { displayName: '새내기', password: 'password1234', consent: true, consentVersion: 'v1' }

async function join(email: string, displayName: string) {
  await post('/api/auth/signup', { email })
  const token = mockState.pending.find((p) => p.email === email)!.token
  return post<{ member: { school: string } }>('/api/auth/verify', { token, ...v, displayName })
}

test('가입 도메인: ac.kr(하위 도메인 포함)과 추가 묶음은 받고, 나머지는 email 필드 400', async () => {
  for (const email of ['new1@snu.ac.kr', 'lee@cs.snu.ac.kr', 'a@skku.edu', 'b@g.skku.edu', 'c@mail.skku.edu', 'D@SNU.AC.KR']) {
    await post('/api/auth/signup', { email })
  }
  for (const email of ['a@stanford.edu', 'a@gmail.com', 'a@ac.kr', 'a@fakeac.kr', 'a@snu.ac.kr.evil.com']) {
    expect(await err(post('/api/auth/signup', { email }))).toMatchObject({
      status: 400,
      fieldErrors: [{ field: 'email', message: '대학 메일로만 가입할 수 있습니다.' }],
    })
  }
})

test('인증을 마치면 학교가 정해지고 member 응답(verify·me·login)에 school이 있다', async () => {
  expect((await join('lee@cs.snu.ac.kr', '이학생')).member.school).toBe('snu.ac.kr')
  expect(await get('/api/auth/me')).toMatchObject({ member: { displayName: '이학생', school: 'snu.ac.kr' } })
  await post('/api/auth/logout')
  expect((await join('b@g.skku.edu', '성학생')).member.school).toBe('skku.edu') // 묶음의 첫 도메인
  await post('/api/auth/logout')
  expect(await post('/api/auth/login', { email: 'organizer@school.ac.kr', password: 'password1234' })).toEqual({ member: { id: 1, displayName: '개설자', school: 'school.ac.kr' } })
})

test('비로그인 목록은 전국이고 카드에 school, school 파라미터로 그 학교만', async () => {
  const all = await list('?status=OPEN')
  expect(new Set(all.items.map((i) => i.school)).size).toBeGreaterThan(1)
  const snu = await list('?school=snu.ac.kr')
  expect(snu.items.length).toBeGreaterThan(0)
  expect(snu.items.every((i) => i.school === 'snu.ac.kr')).toBe(true)
  expect(await err(list(`?school=${'a'.repeat(254)}`))).toMatchObject({ status: 400, fieldErrors: [{ field: 'school' }] })
})

test('로그인하면 school 파라미터를 무시하고 회원 학교로 거른다', async () => {
  mockState.sessionMemberId = 1
  const page = await list('?school=snu.ac.kr')
  expect(page.items.length).toBeGreaterThan(0)
  expect(page.items.every((i) => i.school === 'school.ac.kr')).toBe(true)
})

test('커서는 학교와 묶지 않는다(계약 93ba8f9): 다른 school 커서도 400이 아니다', async () => {
  const anon = await list('?school=school.ac.kr')
  expect(anon.nextCursor).toBeTruthy()
  await list(`?school=snu.ac.kr&cursor=${encodeURIComponent(anon.nextCursor!)}`)
})

test('학교 검색어의 %·_는 글자 그대로 비교한다', async () => {
  mockState.groupBuys = [makeGroupBuy({ school: 'a_b.ac.kr' }), makeGroupBuy({ id: 2, school: 'axb.ac.kr' })]
  expect(await get(`/api/schools?q=${encodeURIComponent('a_b')}`)).toEqual({ items: ['a_b.ac.kr'] })
  expect(await get(`/api/schools?q=${encodeURIComponent('%')}`)).toEqual({ items: [] })
})

test('다른 학교 공구의 상세·참여·수량 변경·참여 취소는 없는 공구와 같은 404', async () => {
  mockState.groupBuys = [makeGroupBuy({ school: 'snu.ac.kr' })]
  mockState.sessionMemberId = 2 // school.ac.kr, 이 공구 참여자로 들어 있어도 학교가 다르면 404
  const body = { quantity: 1, expectedUnitPrice: 1200 }
  for (const p of [get('/api/groupbuys/1'), post('/api/groupbuys/1/participations', body), put('/api/groupbuys/1/participations/me', body), del('/api/groupbuys/1/participations/me')]) {
    expect(await err(p)).toMatchObject({ status: 404, code: 'Not Found' })
  }
  mockState.groupBuys = [makeGroupBuy()]
  expect(await get('/api/groupbuys/1')).toMatchObject({ school: 'school.ac.kr' })
})

test('개설한 공구의 학교는 개설자 학교', async () => {
  mockState.sessionMemberId = 1
  const r = await post<{ id: number }>('/api/groupbuys', {
    title: '햇반', itemName: '햇반', unitPrice: 1200, totalQuantity: 24, organizerQuantity: 9, category: 'FOOD',
    itemUrl: 'https://example.com/a', openChatUrl: 'https://open.kakao.com/o/x', deadline: new Date(Date.now() + 86_400_000).toISOString(),
  })
  expect(mockState.groupBuys.find((g) => g.id === r.id)!.school).toBe('school.ac.kr')
})

test('학교 검색 /api/schools: 공구가 있는 학교 키 중 q가 들어간 것, 사전순 최대 10개', async () => {
  expect(await get('/api/schools?q=snu')).toEqual({ items: ['snu.ac.kr', 'snue.ac.kr'] })
  expect(await get(`/api/schools?q=${encodeURIComponent('  SNU ')}`)).toEqual({ items: ['snu.ac.kr', 'snue.ac.kr'] })
  expect(await get('/api/schools?q=zzz')).toEqual({ items: [] })
  mockState.groupBuys = Array.from({ length: 12 }, (_, i) => makeGroupBuy({ id: i + 1, school: `u${String(i).padStart(2, '0')}.ac.kr` }))
  expect((await get<{ items: string[] }>('/api/schools?q=ac.kr')).items).toEqual(Array.from({ length: 10 }, (_, i) => `u${String(i).padStart(2, '0')}.ac.kr`))
  for (const q of ['', '%20%20', 'a'.repeat(51)]) expect(await err(get(`/api/schools?q=${q}`))).toMatchObject({ status: 400, fieldErrors: [{ field: 'q' }] })
  expect(await err(get('/api/schools'))).toMatchObject({ status: 400, fieldErrors: [{ field: 'q' }] })
})
