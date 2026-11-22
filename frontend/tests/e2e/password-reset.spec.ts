import { expect, test, type Page } from '@playwright/test'

// 목 모드 기준. 011 비밀번호 재설정과 개인정보 처리방침 화면. 목 상태는 메모리라 앱 안에서(popstate) 이동한다.
const clientNavigate = (page: Page, url: string) =>
  page.evaluate((u) => {
    history.pushState({}, '', u)
    dispatchEvent(new PopStateEvent('popstate'))
  }, url)
const noHorizontalScroll = (page: Page) => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)

test('로그인 화면에서 재설정을 요청하고 링크로 새 비밀번호를 정한 뒤 새 비밀번호로만 로그인된다(키보드만)', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('link', { name: '비밀번호를 잊었어요' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('heading', { name: '비밀번호 재설정', level: 1 })).toBeVisible()
  await page.getByLabel('이메일').focus()
  await page.keyboard.type('organizer@school.ac.kr')
  await page.keyboard.press('Enter')
  await expect(page.getByRole('status')).toContainText('가입한 학교 메일이면 재설정 링크를 보냈어요.')
  expect(await noHorizontalScroll(page)).toBe(true)

  const token = await page.evaluate(() => (window as unknown as { __mock: { passwordResets: { email: string; token: string | null }[] } }).__mock.passwordResets.find((r) => r.email === 'organizer@school.ac.kr')!.token)
  await clientNavigate(page, `/password-reset/confirm?token=${token}`)
  await page.getByLabel('새 비밀번호', { exact: true }).focus()
  await page.keyboard.type('new-password-1')
  await page.keyboard.press('Tab') // 안내 문단은 포커스를 받지 않아 바로 확인 칸으로
  await expect(page.getByLabel('새 비밀번호 확인')).toBeFocused()
  await page.keyboard.type('new-password-1')
  await page.keyboard.press('Enter')

  const notice = page.getByText('비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.')
  await expect(notice).toBeFocused()
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('이메일', { exact: true }).fill('organizer@school.ac.kr')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByText('이메일 또는 비밀번호가 맞지 않아요.')).toBeVisible()
  await page.getByLabel('비밀번호').fill('new-password-1')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()
})

test('쓸 수 없는 링크는 폼 없이 이유와 다시 요청 링크', async ({ page }) => {
  await page.goto('/password-reset/confirm?token=nope')
  await page.getByLabel('새 비밀번호', { exact: true }).fill('new-password-1')
  await page.getByLabel('새 비밀번호 확인').fill('new-password-1')
  await page.getByRole('button', { name: '비밀번호 바꾸기' }).click()
  await expect(page.getByRole('alert')).toHaveText('올바르지 않은 링크예요.')
  await expect(page.getByRole('link', { name: '다시 요청하기' })).toHaveAttribute('href', '/password-reset')
  await expect(page.getByLabel('새 비밀번호', { exact: true })).toHaveCount(0)
})

test('모든 화면 아래 처리방침 링크는 굵게 구분되고, 처리방침 화면은 로그인 없이 열리며 가로로 넘치지 않는다', async ({ page }) => {
  await page.goto('/')
  const link = page.getByRole('contentinfo').getByRole('link', { name: '개인정보 처리방침' })
  await expect(link).toBeVisible()
  expect(Number(await link.evaluate((el) => getComputedStyle(el).fontWeight))).toBeGreaterThanOrEqual(700)
  await link.click()
  await expect(page).toHaveURL(/\/privacy$/)
  await expect(page.getByRole('heading', { name: '캠공 개인정보 처리방침', level: 1 })).toBeVisible()
  await expect(page.getByRole('table', { name: '국외 이전 내용' })).toBeVisible()
  expect(await noHorizontalScroll(page)).toBe(true)
})
