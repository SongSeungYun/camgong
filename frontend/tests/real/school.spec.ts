import { expect, test, type Browser, type Page, type TestInfo } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { ORIGIN } from '../../playwright.real.config'

// 009 T015: 실제 백엔드에서 학교 범위. 두 학교(이번 실행만의 ac.kr 도메인)를 만들어 확인한다.
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

async function member(browser: Browser, info: TestInfo, email: string, name: string) {
  const ctx = await browser.newContext({ viewport: info.project.use.viewport, extraHTTPHeaders: { Origin: ORIGIN } })
  const page = await ctx.newPage()
  await page.goto('/signup')
  await page.getByLabel('이메일', { exact: true }).fill(email)
  const before = links().length
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByRole('heading', { name: '인증 메일을 보냈어요' })).toBeVisible()
  await page.goto(`/verify?token=${await newToken(before)}`)
  await page.getByLabel('표시 이름', { exact: true }).fill(name)
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '인증 완료하기' }).click()
  await expect(page).toHaveURL(/\/$/)
  return { ctx, page }
}

const deadline = () => new Date(Date.now() + 3 * 86_400_000 + 9 * 3_600_000).toISOString().slice(0, 10) + 'T18:00:00+09:00'
async function open(page: Page, title: string) {
  const r = await page.context().request.post('/api/groupbuys', {
    data: { title, description: null, category: 'FOOD', itemName: '햇반', itemUrl: 'https://example.com/i', unitPrice: 1200, totalQuantity: 10, organizerQuantity: 3, deadline: deadline(), openChatUrl: 'https://open.kakao.com/o/x' },
  })
  expect(r.status()).toBe(201)
  return ((await r.json()) as { id: number }).id
}

test('실서버 009: 학교 범위(비로그인 학교 검색, 로그인 우리 학교, 다른 학교 공구 404)', async ({ browser, page }, info) => {
  const v = info.project.name.slice(-3)
  const schoolA = `ua${tag}${v}.ac.kr`
  const schoolB = `ub${tag}${v}.ac.kr`
  const a = await member(browser, info, `a${tag}${v}@cs.${schoolA}`, `가${tag}${v}`) // 하위 도메인도 그 학교
  const b = await member(browser, info, `b${tag}${v}@${schoolB}`, `나${tag}${v}`)
  const titleA = `A학교공구${tag}${v}`
  const titleB = `B학교공구${tag}${v}`
  await open(a.page, titleA)
  const idB = await open(b.page, titleB)

  // 비로그인: 학교 검색으로 A 학교를 키보드로 골라 거른다. 새로고침해도 유지
  await page.goto('/')
  const combo = page.getByRole('combobox', { name: '학교 검색' })
  await combo.focus()
  await page.keyboard.type(`ua${tag}${v}`)
  await expect(page.getByRole('listbox', { name: '학교 후보' }).getByRole('option')).toHaveText([schoolA])
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(new RegExp(`school=${schoolA.replace(/\./g, '\\.')}`))
  await expect(page.getByText(`선택한 학교 · ${schoolA}`)).toBeVisible()
  await expect(page.getByRole('link', { name: new RegExp(titleA) })).toBeVisible()
  await expect(page.getByRole('link', { name: new RegExp(titleB) })).toHaveCount(0)
  await expect.poll(async () => new Set(await page.getByRole('listitem').getByText(/^학교 · /).allTextContents())).toEqual(new Set([`학교 · ${schoolA}`]))
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.reload()
  await expect(page.getByText(`선택한 학교 · ${schoolA}`)).toBeVisible()
  await expect(page.getByRole('link', { name: new RegExp(titleA) })).toBeVisible()

  // 로그인한 A: 학교 검색 없이 우리 학교 머리말, B 학교 공구는 검색해도 없다
  await a.page.goto(`/?school=${schoolB}&q=${encodeURIComponent(titleB)}`)
  await expect(a.page.getByText(`우리 학교 · ${schoolA}`)).toBeVisible()
  await expect(a.page.getByRole('combobox', { name: '학교 검색' })).toHaveCount(0)
  await expect(a.page.getByText('검색 결과가 없어요. 다른 검색어나 조건을 써보세요.')).toBeVisible()

  // 다른 학교 공구 번호로 직접: 상세는 없는 공구와 같고, 참여도 404
  await a.page.goto(`/groupbuys/${idB}`)
  await expect(a.page.getByText('찾을 수 없는 공구예요.')).toBeVisible()
  const join = await a.ctx.request.post(`/api/groupbuys/${idB}/participations`, { data: { quantity: 1, expectedUnitPrice: 1200 } })
  expect(join.status()).toBe(404)

  await a.ctx.close()
  await b.ctx.close()
})

test('실서버 009: ac.kr이 아닌 메일은 가입 화면에서 이메일 옆에 서버 문구', async ({ page }) => {
  await page.goto('/signup')
  await page.getByLabel('이메일', { exact: true }).fill(`x${tag}@gmail.com`)
  await page.getByRole('button', { name: '가입하기' }).click()
  const input = page.getByLabel('이메일', { exact: true })
  await expect(input).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByText('대학 메일로만 가입할 수 있습니다.')).toBeVisible()
})
