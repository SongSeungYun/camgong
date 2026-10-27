import { expect, test, type Page } from '@playwright/test'

// 목 모드 기준. quickstart 35, 34. 모든 화면(S01~S10 + 404)에서 가로 스크롤이 없고 긴 글이 줄바꿈된다.
const LONG = '가'.repeat(120) + 'A'.repeat(80) // 공백 없는 긴 글
interface Mock {
  members: { id: number; displayName: string }[]
  groupBuys: { id: number; title: string; status: string; organizerId: number; participations: { memberId: number; quantity: number }[] }[]
  notifications: { groupBuyTitle: string }[]
}
const clientNavigate = (page: Page, url: string) =>
  page.evaluate((u) => {
    history.pushState({}, '', u)
    dispatchEvent(new PopStateEvent('popstate'))
  }, url)

async function noHorizontalScroll(page: Page, where: string) {
  const m = await page.evaluate(() => ({ sw: document.documentElement.scrollWidth, iw: window.innerWidth }))
  expect(m.sw, `${where} 가로 스크롤`).toBeLessThanOrEqual(m.iw)
}

test('비로그인 화면: 목록·로그인·가입·인증·없는 주소', async ({ page }) => {
  for (const path of ['/', '/login', '/signup', '/verify?token=expired-token', '/nowhere']) {
    await page.goto(path)
    await expect(page.locator('main, h1').first()).toBeVisible()
    await noHorizontalScroll(page, path)
  }
})

test('로그인 화면: 긴 제목·긴 이름·긴 알림이 있어도 가로 스크롤이 없다', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill('organizer@school.ac.kr')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)

  // 개설자(1)의 모집 완료 공구 하나에 긴 제목과 긴 이름의 참여자를 만든다
  const id = await page.evaluate((long) => {
    const m = (window as unknown as { __mock: Mock }).__mock
    const g = m.groupBuys.find((x) => x.organizerId === 1 && x.participations.some((p) => p.memberId !== 1))!
    g.status = 'COMPLETED' // 체크표는 모집 완료에서만 열린다
    g.title = long
    for (const p of g.participations) {
      const mem = m.members.find((x) => x.id === p.memberId)!
      if (p.memberId !== 1) mem.displayName = long.slice(0, 40)
    }
    m.notifications.forEach((n) => (n.groupBuyTitle = long))
    return g.id
  }, LONG)

  for (const path of ['/', `/groupbuys/${id}`, '/groupbuys/new', `/groupbuys/${id}/check`, '/me', '/me?tab=participant', '/notifications']) {
    await clientNavigate(page, path)
    await expect(page.getByRole('heading').first()).toBeVisible()
    await page.waitForLoadState('networkidle')
    await noHorizontalScroll(page, path)
  }
  // 긴 제목은 그대로(잘리지 않고) 화면에 있다
  await clientNavigate(page, `/groupbuys/${id}`)
  await expect(page.getByRole('heading', { name: LONG })).toBeVisible()
  await noHorizontalScroll(page, 'detail')
})

test('개설자의 수정 화면', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill('organizer@school.ac.kr')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
  const id = await page.evaluate(() => (window as unknown as { __mock: Mock }).__mock.groupBuys.find((x) => x.status === 'OPEN' && x.organizerId === 1)!.id)
  await clientNavigate(page, `/groupbuys/${id}/edit`)
  await expect(page.getByLabel('제목')).toBeVisible()
  await noHorizontalScroll(page, 'edit')
})
