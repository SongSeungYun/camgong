import { expect, test, type APIRequestContext, type Browser, type Page, type TestInfo } from '@playwright/test'
import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { ORIGIN } from '../../playwright.real.config'

// 010 T014: 실제 백엔드에서 탈퇴. 비밀번호 5회 실패는 로그인까지 15분 잠그므로 틀린 비밀번호는 계정마다 한 번만 쓴다.
const LOG = process.env.BACKEND_LOG!
const tag = Date.now().toString().slice(-6)
const links = () => readFileSync(LOG, 'utf8').split('\n').filter((l) => l.includes('verification link='))
async function newToken(before: number): Promise<string> {
  for (let i = 0; i < 50; i++) {
    const all = links()
    if (all.length > before) return /token=([^\s&]+)/.exec(all[all.length - 1]!)![1]!
    await new Promise((r) => setTimeout(r, 200))
  }
  throw new Error('인증 링크가 로그에 나오지 않았다')
}
const sql = (q: string) => execSync(`docker exec camgong-mysql-local mysql -uroot -ppw camgong -e "${q}"`, { stdio: 'pipe' })

async function user(browser: Browser, info: TestInfo, email: string, name: string) {
  const ctx = await browser.newContext({ viewport: info.project.use.viewport, extraHTTPHeaders: { Origin: ORIGIN } })
  const before = links().length
  await ctx.request.post('/api/auth/signup', { data: { email } })
  const r = await ctx.request.post('/api/auth/verify', { data: { token: await newToken(before), displayName: name, password: 'password1234', consent: true, consentVersion: 'v1' } })
  expect(r.status()).toBe(200)
  return { ctx, page: await ctx.newPage(), api: ctx.request }
}
const deadline = () => new Date(Date.now() + 3 * 86_400_000 + 9 * 3_600_000).toISOString().slice(0, 10) + 'T18:00:00+09:00'
async function open(api: APIRequestContext, title: string, total = 10, mine = 3) {
  const r = await api.post('/api/groupbuys', {
    data: { title, description: null, category: 'FOOD', itemName: '햇반', itemUrl: 'https://example.com/i', unitPrice: 1200, totalQuantity: total, organizerQuantity: mine, deadline: deadline(), openChatUrl: 'https://open.kakao.com/o/x' },
  })
  expect(r.status()).toBe(201)
  return ((await r.json()) as { id: number }).id
}
async function withdrawUi(page: Page, password: string) {
  await page.goto('/me')
  await page.getByRole('region', { name: '탈퇴' }).getByRole('button', { name: '탈퇴' }).click()
  const dialog = page.getByRole('dialog', { name: '탈퇴할까요?' })
  await expect(dialog.getByLabel('비밀번호')).toBeFocused()
  await dialog.getByLabel('비밀번호').fill(password)
  await page.keyboard.press('Enter')
  return dialog
}

test('실서버 010: 진행 중 공구가 있으면 막히고 공구별 안내(마감 대기는 개설자여도 확정 대기)', async ({ browser }, info) => {
  const v = info.project.name.slice(-3)
  const o = await user(browser, info, `wo${tag}${v}@school.ac.kr`, `탈개${tag}${v}`)
  const p = await user(browser, info, `wp${tag}${v}@school.ac.kr`, `탈참${tag}${v}`)
  const openId = await open(o.api, `열린공구${tag}${v}`)
  const closedId = await open(o.api, `마감공구${tag}${v}`)
  expect((await p.api.post(`/api/groupbuys/${openId}/participations`, { data: { quantity: 1, expectedUnitPrice: 1200 } })).status()).toBe(201)
  sql(`UPDATE group_buy SET deadline = UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE WHERE id=${closedId}`) // 마감 지남(확정 대기)

  const dialog = await withdrawUi(o.page, 'password1234')
  const alert = o.page.getByRole('alert', { name: '진행 중인 공구가 있어 탈퇴할 수 없어요.' })
  await expect(alert).toBeVisible()
  await expect(dialog).toHaveCount(0)
  await expect(alert.getByRole('listitem')).toHaveText([`열린공구${tag}${v} — 공구를 취소하세요`, `마감공구${tag}${v} — 확정된 뒤 다시 시도하세요`])
  await expect(alert.getByRole('link', { name: `열린공구${tag}${v}` })).toHaveAttribute('href', `/groupbuys/${openId}`)
  expect(await o.page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await withdrawUi(p.page, 'password1234')
  await expect(p.page.getByRole('alert', { name: '진행 중인 공구가 있어 탈퇴할 수 없어요.' }).getByRole('listitem')).toHaveText([`열린공구${tag}${v} — 참여를 취소하세요`])
  await expect(p.page.getByRole('button', { name: '로그아웃' })).toBeVisible() // 막혀도 로그인 그대로

  sql(`UPDATE group_buy SET deadline = UTC_TIMESTAMP(6) + INTERVAL 1 DAY WHERE id=${closedId}`) // 다른 시험에 영향 없게 되돌린다
  await o.ctx.close()
  await p.ctx.close()
})

test('실서버 010: 틀린 비밀번호 → 맞는 비밀번호로 탈퇴, 다른 세션 끊김, 로그인 불가, 체크표에 "탈퇴한 회원", 같은 이메일 재가입·예약 이름 거부', async ({ browser, page }, info) => {
  const v = info.project.name.slice(-3)
  const email = `wq${tag}${v}@school.ac.kr`
  const name = `떠남${tag}${v}`
  const o = await user(browser, info, `wr${tag}${v}@school.ac.kr`, `체크${tag}${v}`)
  const q = await user(browser, info, email, name)
  const other = await browser.newContext({ viewport: info.project.use.viewport, extraHTTPHeaders: { Origin: ORIGIN } }) // 같은 계정의 다른 기기
  expect((await other.request.post('/api/auth/login', { data: { email, password: 'password1234' } })).status()).toBe(200)
  const id = await open(o.api, `완료공구${tag}${v}`, 4, 2)
  expect((await q.api.post(`/api/groupbuys/${id}/participations`, { data: { quantity: 2, expectedUnitPrice: 1200 } })).status()).toBe(201) // 마지막 수량 → 모집 완료

  const dialog = await withdrawUi(q.page, 'wrong-password')
  await expect(dialog.getByText('비밀번호가 맞지 않습니다.')).toBeVisible()
  await expect(dialog.getByLabel('비밀번호')).toBeFocused()
  await dialog.getByLabel('비밀번호').fill('password1234')
  await q.page.keyboard.press('Enter')
  await expect(q.page.getByText('탈퇴했어요.')).toBeVisible()
  await expect(q.page).toHaveURL(/\/$/)
  await expect(q.page.getByRole('link', { name: '로그인' })).toBeVisible()

  expect((await other.request.get('/api/auth/me')).status()).toBe(401) // 다른 세션도 끝남
  expect((await q.api.post('/api/auth/login', { data: { email, password: 'password1234' } })).status()).toBe(401)

  await o.page.goto(`/groupbuys/${id}/check`)
  const row = o.page.getByRole('listitem', { name: '탈퇴한 회원' })
  await expect(row).toBeVisible()
  await expect(row.getByText(/2개/)).toBeVisible()
  await row.getByRole('button', { name: '입금 확인' }).click() // 개설자는 계속 체크할 수 있다
  await expect(row.getByText(/입금 확인됨/)).toBeVisible()

  // 같은 이메일로 다시 가입: 예약 이름은 화면에서 거부, 옛 이름은 다시 쓸 수 있다
  await page.goto('/signup')
  await page.getByLabel('이메일', { exact: true }).fill(email)
  const before = links().length
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByRole('heading', { name: '인증 메일을 보냈어요' })).toBeVisible()
  await page.goto(`/verify?token=${await newToken(before)}`)
  await page.getByLabel('표시 이름', { exact: true }).fill('탈퇴한  회원')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '인증 완료하기' }).click()
  await expect(page.getByText('쓸 수 없는 이름입니다.')).toBeVisible()
  await page.getByLabel('표시 이름', { exact: true }).fill(name)
  await page.getByRole('button', { name: '인증 완료하기' }).click()
  await expect(page).toHaveURL(/\/$/)

  await o.ctx.close()
  await q.ctx.close()
  await other.close()
})

test('실서버 010: 인증 화면의 동의문 본문을 펼쳐 볼 수 있다', async ({ page }) => {
  await page.goto('/signup')
  await page.getByLabel('이메일', { exact: true }).fill(`wc${tag}${test.info().project.name.slice(-3)}@school.ac.kr`)
  const before = links().length
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByRole('heading', { name: '인증 메일을 보냈어요' })).toBeVisible()
  await page.goto(`/verify?token=${await newToken(before)}`)
  await page.getByText('자세히 보기').click()
  await expect(page.getByText('캠공은 같은 학교 학생끼리 공동구매를 하기 위해 아래 정보를 받습니다.')).toBeVisible()
  await expect(page.getByRole('table', { name: '받는 정보와 목적' })).toBeVisible()
})
