import { expect, test, type Page } from '@playwright/test'

// 목 모드 기준. 기본 접근성(T062): 입력마다 레이블, 이름 없는 버튼·링크 없음, 버튼·입력 터치 대상 44px, 키보드 포커스 표시.
const clientNavigate = (page: Page, url: string) =>
  page.evaluate((u) => {
    history.pushState({}, '', u)
    dispatchEvent(new PopStateEvent('popstate'))
  }, url)

async function audit(page: Page, where: string) {
  const problems = await page.evaluate(() => {
    const out: string[] = []
    const name = (el: Element) => (el.getAttribute('aria-label') || el.textContent || '').trim()
    for (const el of document.querySelectorAll('input:not([type=hidden]), select, textarea')) {
      const i = el as HTMLInputElement
      if (!(i.labels && i.labels.length) && !el.getAttribute('aria-label')) out.push(`레이블 없는 입력 ${i.name || i.type}`)
    }
    for (const el of document.querySelectorAll('button, a[href]')) if (!name(el)) out.push(`이름 없는 ${el.tagName}`)
    for (const el of document.querySelectorAll('button, select, input:not([type=checkbox]):not([type=radio]):not([type=hidden]), textarea')) {
      const r = el.getBoundingClientRect()
      if (r.width > 0 && r.height > 0 && r.height < 43.5) out.push(`터치 대상 ${Math.round(r.height)}px: ${name(el) || el.tagName}`)
    }
    return out
  })
  expect(problems, where).toEqual([])
}

async function login(page: Page, email: string) {
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill(email)
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
}

test('비로그인 화면의 레이블·이름·터치 대상', async ({ page }) => {
  for (const path of ['/', '/login', '/signup', '/verify?token=expired-token']) {
    await page.goto(path)
    await expect(page.getByRole('heading').first()).toBeVisible()
    await audit(page, path)
  }
})

test('로그인 화면의 레이블·이름·터치 대상', async ({ page }) => {
  await login(page, 'organizer@school.ac.kr')
  for (const path of ['/', '/groupbuys/new', '/me', '/notifications']) {
    await clientNavigate(page, path)
    await expect(page.getByRole('heading').first()).toBeVisible()
    await page.waitForLoadState('networkidle')
    await audit(page, path)
  }
})

test('키보드만으로 로그인 폼을 채우고 제출하며, 포커스가 보인다', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).focus()
  await page.keyboard.type('organizer@school.ac.kr')
  await page.keyboard.press('Tab')
  await page.keyboard.type('password1234')
  const outline = await page.evaluate(() => {
    document.querySelector<HTMLElement>('button[type=submit]')?.focus()
    return getComputedStyle(document.activeElement!).outlineStyle
  })
  expect(outline).not.toBe('none')
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/$/)
})
