import { expect, test } from '@playwright/test'

// 목 모드 기준. 009 학교 범위: 비로그인 학교 검색(키보드만), 주소 유지, 로그인 시 우리 학교 머리말.
// 시드: 대부분 school.ac.kr, 모집 중 snu.ac.kr 공구 1개(48), snue.ac.kr 2개(49·50)
const cardSchools = (page: import('@playwright/test').Page) =>
  page.getByRole('listitem').getByText(/^학교 /).allTextContents()

test('비로그인: 키보드만으로 학교를 골라 목록을 거르고, 새로고침해도 유지되며 가로 스크롤이 없다', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('listitem').first()).toBeVisible()
  const combo = page.getByRole('combobox', { name: '학교 검색' })
  await combo.focus()
  await page.keyboard.type('snu')
  const box = page.getByRole('listbox', { name: '학교 후보' })
  await expect(box.getByRole('option')).toHaveText(['snu.ac.kr', 'snue.ac.kr'])
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('ArrowDown')
  await expect(combo).toHaveAttribute('aria-activedescendant', (await box.getByRole('option').nth(1).getAttribute('id'))!)
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/school=snue\.ac\.kr/)
  await expect(combo).toBeFocused()
  await expect(page.getByText('선택한 학교: snue.ac.kr')).toBeVisible()
  await expect.poll(() => cardSchools(page)).toEqual(['학교 snue.ac.kr', '학교 snue.ac.kr'])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await page.reload()
  await expect(page.getByText('선택한 학교: snue.ac.kr')).toBeVisible()
  await expect.poll(() => cardSchools(page)).toEqual(['학교 snue.ac.kr', '학교 snue.ac.kr'])

  await page.getByRole('button', { name: '학교 선택 해제' }).focus()
  await page.keyboard.press('Enter')
  await expect(page).not.toHaveURL(/school=/)
  await expect(combo).toBeFocused() // 사라진 해제 버튼 대신 입력칸으로
})

test('로그인하면 학교 검색이 없고 우리 학교 공구만 보인다', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill('organizer@school.ac.kr')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText('우리 학교 · school.ac.kr')).toBeVisible()
  await expect(page.getByRole('combobox', { name: '학교 검색' })).toHaveCount(0)
  await expect.poll(async () => new Set(await cardSchools(page))).toEqual(new Set(['학교 school.ac.kr']))
})
