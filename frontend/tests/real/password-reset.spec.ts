import { expect, test } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { ORIGIN } from '../../playwright.real.config'

// 011 T013: 실제 백엔드에서 비밀번호 재설정. 같은 주소는 1분·24시간 5회 한도라 실행·화면 크기마다 새 주소를 쓴다.
const LOG = process.env.BACKEND_LOG!
const tag = Date.now().toString().slice(-6)
const lines = (kind: string) => readFileSync(LOG, 'utf8').split('\n').filter((l) => l.includes(`${kind} link=`))
async function newToken(kind: string, before: number): Promise<string> {
  for (let i = 0; i < 50; i++) {
    const all = lines(kind)
    if (all.length > before) return /token=([^\s&]+)/.exec(all[all.length - 1]!)![1]!
    await new Promise((r) => setTimeout(r, 200))
  }
  throw new Error(`${kind} 링크가 로그에 나오지 않았다`)
}

test('실서버 011: 재설정 요청 → 로그의 링크로 새 비밀번호 → 로그인 안내, 새 비밀번호만 로그인, 옛 세션 401', async ({ browser, page }, info) => {
  const v = info.project.name.slice(-3)
  const email = `pr${tag}${v}@school.ac.kr`
  const old = await browser.newContext({ viewport: info.project.use.viewport, extraHTTPHeaders: { Origin: ORIGIN } }) // 재설정 전에 로그인한 기기
  const before = lines('verification').length
  await old.request.post('/api/auth/signup', { data: { email } })
  const r = await old.request.post('/api/auth/verify', { data: { token: await newToken('verification', before), displayName: `재설${tag}${v}`, password: 'password1234', consent: true, consentVersion: 'v1' } })
  expect(r.status()).toBe(200)
  expect((await old.request.get('/api/auth/me')).status()).toBe(200)

  await page.goto('/login')
  await page.getByRole('link', { name: '비밀번호를 잊었어요' }).click()
  await expect(page.getByRole('heading', { name: '비밀번호 재설정', level: 1 })).toBeVisible()
  await page.getByLabel('이메일', { exact: true }).fill(email)
  const beforeReset = lines('password reset').length
  await page.getByRole('button', { name: '재설정 링크 받기' }).click()
  await expect(page.getByRole('status')).toContainText('가입한 학교 메일이면 재설정 링크를 보냈어요.')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await page.goto(`/password-reset/confirm?token=${await newToken('password reset', beforeReset)}`)
  await page.getByLabel('새 비밀번호', { exact: true }).fill('new-password-1')
  await page.getByLabel('새 비밀번호 확인').fill('new-password-1')
  await page.getByRole('button', { name: '비밀번호 바꾸기' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByText('비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.')).toBeFocused()

  await page.getByLabel('이메일', { exact: true }).fill(email)
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByText('이메일 또는 비밀번호가 맞지 않아요.')).toBeVisible()
  await page.getByLabel('비밀번호').fill('new-password-1')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()

  expect((await old.request.get('/api/auth/me')).status()).toBe(401) // 재설정 전 세션은 끝남
  await old.close()
})
