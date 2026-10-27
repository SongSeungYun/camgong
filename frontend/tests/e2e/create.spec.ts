import { expect, test } from '@playwright/test'

// 목 모드 기준. quickstart 16·17, SC-004.
test('틀린 값 여러 개 → 필드별 오류 → 올바른 값으로 개설 → 새 상세', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill('organizer@school.ac.kr')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.getByRole('link', { name: '공구 열기' }).click()
  await expect(page).toHaveURL(/\/groupbuys\/new$/)

  const future = new Date(Date.now() + 2 * 86_400_000)
  const kst = new Date(future.getTime() + 9 * 3_600_000).toISOString().slice(0, 16)

  await page.getByLabel('제목').fill('e2e 공구')
  await page.getByLabel('카테고리').selectOption('FOOD')
  await page.getByLabel('상품명').fill('e2e 상품')
  await page.getByLabel('구매처 링크').fill('http://insecure.example.com')
  await page.getByLabel('개당 가격').fill('0')
  await page.getByLabel('총 수량').fill('24')
  await page.getByLabel('내 수량').fill('30')
  await page.getByLabel('마감 (한국 시각)').fill(kst)
  await page.getByLabel('오픈채팅 링크').fill('https://open.kakao.com/o/e2e')
  await page.getByRole('button', { name: '공구 열기' }).click()

  for (const label of ['구매처 링크', '개당 가격', '내 수량']) {
    await expect(page.getByLabel(label)).toHaveAttribute('aria-invalid', 'true')
  }
  await expect(page.getByLabel('제목')).toHaveAttribute('aria-invalid', 'false')
  await expect(page.getByLabel('제목')).toHaveValue('e2e 공구') // 입력값 유지

  await page.getByLabel('구매처 링크').fill('https://example.com/e2e')
  await page.getByLabel('개당 가격').fill('1200')
  await page.getByLabel('내 수량').fill('9')
  await page.getByRole('button', { name: '공구 열기' }).click()
  await expect(page).toHaveURL(/\/groupbuys\/\d+$/)
  await expect(page.getByRole('heading', { name: 'e2e 공구' })).toBeVisible()
  const noHScroll = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
  expect(noHScroll).toBe(true)
})
