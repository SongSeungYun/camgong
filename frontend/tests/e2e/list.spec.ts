import { expect, test } from '@playwright/test'

// 목 모드(VITE_USE_MOCK=true) 데이터 기준. quickstart 3·35.
test('목록: 조건 변경이 주소에 반영되고 새로고침해도 유지되며 가로 스크롤이 없다', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('link', { name: /공구/ }).first()).toBeVisible()

  await page.getByRole('button', { name: '식품' }).click()
  await expect(page).toHaveURL(/category=FOOD/)

  await page.getByRole('searchbox').fill('햇반')
  await expect(page).toHaveURL(/q=/)

  await page.reload()
  await expect(page).toHaveURL(/category=FOOD/)
  await expect(page.getByRole('button', { name: '식품' })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('searchbox')).toHaveValue('햇반')

  const noHScroll = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
  expect(noHScroll).toBe(true)
})

test('상세에 갔다 뒤로 오면 목록 위치와 불러온 페이지가 유지된다', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill('organizer@school.ac.kr')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)

  await page.getByRole('button', { name: '더 보기' }).click()
  await expect(page.getByRole('listitem')).toHaveCount(26)
  await page.getByRole('listitem').nth(18).scrollIntoViewIfNeeded()
  const before = await page.evaluate(() => window.scrollY)
  expect(before).toBeGreaterThan(0)

  await page.getByRole('listitem').nth(18).getByRole('link').click()
  await expect(page).toHaveURL(/\/groupbuys\/\d+$/)
  await page.goBack()
  await expect(page.getByRole('listitem')).toHaveCount(26) // 쿼리 캐시에서 복원
  const after = await page.evaluate(() => window.scrollY)
  expect(Math.abs(after - before)).toBeLessThan(200)
})
