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
