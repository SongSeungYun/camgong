import { expect, test, type Page } from '@playwright/test'

// 목 모드 데이터 기준. quickstart 7·9·31.
// 목 상태는 메모리라 page.goto(새로고침)하면 초기화된다. 같은 세션 안의 이동은 앱 안에서(popstate) 한다.
const mock = (page: Page) => page.evaluate(() => (window as unknown as { __mock: { pending: { token: string }[]; sessionExpired: boolean } }).__mock)
const clientNavigate = (page: Page, url: string) =>
  page.evaluate((u) => {
    history.pushState({}, '', u)
    dispatchEvent(new PopStateEvent('popstate'))
  }, url)

async function login(page: Page) {
  await page.getByLabel('이메일', { exact: true }).fill('organizer@school.ac.kr')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
}

test('보호 라우트 → 로그인 → /, 로그아웃', async ({ page }) => {
  await page.goto('/groupbuys/1')
  await expect(page).toHaveURL(/\/login$/)
  await login(page)
  await expect(page).toHaveURL(/\/$/)
  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/login$/)
})

test('이메일만 가입 → 인증 링크 → 이름·비밀번호·동의 입력 → 로그인된 목록', async ({ page }) => {
  await page.goto('/signup')
  await expect(page.getByLabel('비밀번호')).toHaveCount(0) // 가입 화면은 이메일만 받는다
  await page.getByLabel('이메일', { exact: true }).fill('e2e@school.ac.kr')
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByRole('heading', { name: '인증 메일을 보냈어요' })).toBeVisible()

  const { pending } = await mock(page) // 메일 대신 목이 만든 토큰을 쓴다
  await clientNavigate(page, `/verify?token=${pending[0]!.token}`)
  await page.getByLabel('표시 이름', { exact: true }).fill('이투이')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '인증 완료하기' }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()
})

test('만료된 인증 링크는 이유와 재발송을 보여준다', async ({ page }) => {
  await page.goto('/verify?token=expired-token')
  await page.getByLabel('표시 이름', { exact: true }).fill('이투이')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '인증 완료하기' }).click()
  await expect(page.getByText('링크가 만료됐어요.')).toBeVisible()
  await expect(page.getByRole('button', { name: '인증 메일 다시 받기' })).toBeVisible()
})

test('사용 중 세션 만료(401) → 로그인 + 입력 소실 안내', async ({ page }) => {
  await page.goto('/login')
  await login(page)
  await expect(page).toHaveURL(/\/$/)
  await page.evaluate(() => {
    ;(window as unknown as { __mock: { sessionExpired: boolean } }).__mock.sessionExpired = true
  })
  await clientNavigate(page, '/groupbuys/6')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByText('로그인이 필요해요. 입력하던 내용은 저장되지 않아요.')).toBeVisible()
})
