import { expect, test, type Page } from '@playwright/test'

// 목 모드 기준. quickstart 21·22·26. 목 상태는 메모리라 새로고침하면 초기화되므로 앱 안에서 이동한다.
interface Mock {
  priceChangeTo: number | null
  groupBuys: { id: number; status: string; organizerId: number; participations: { memberId: number; quantity: number }[] }[]
}
const mock = <T>(page: Page, fn: (m: Mock) => T) => page.evaluate(`(${fn.toString()})(window.__mock)`) as Promise<T>
const clientNavigate = (page: Page, url: string) =>
  page.evaluate((u) => {
    history.pushState({}, '', u)
    dispatchEvent(new PopStateEvent('popstate'))
  }, url)

async function loginAsOutsider(page: Page) {
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill('outsider@school.ac.kr')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
}

// 구경꾼(3)이 개설자가 아니고 아직 참여하지 않은 모집 중 공구 id들
const joinable = (page: Page) =>
  mock(page, (m) => m.groupBuys.filter((g) => g.status === 'OPEN' && g.organizerId !== 3 && !g.participations.some((p) => p.memberId === 3)).map((g) => g.id))

test('참여하면 수치가 바뀌고, 연타해도 한 번만 반영된다', async ({ page }) => {
  await loginAsOutsider(page)
  const [id] = await joinable(page)
  await clientNavigate(page, `/groupbuys/${id}`)
  await page.getByRole('button', { name: '참여하기' }).click()
  await page.getByLabel('수량').fill('2')
  await page.getByRole('button', { name: '참여 신청' }).dblclick()
  await expect(page.getByText(/내 참여: 2개/)).toBeVisible()
  await expect(page.getByRole('button', { name: '수량 변경' })).toBeVisible()
  const mine = await page.evaluate(
    (gid) => (window as unknown as { __mock: Mock }).__mock.groupBuys.find((g) => g.id === gid)!.participations.filter((p) => p.memberId === 3),
    id!,
  )
  expect(mine).toEqual([{ memberId: 3, quantity: 2 }]) // 한 건만
})

test('가격이 바뀌면 새 가격을 안내하고 다시 확인해야 제출된다', async ({ page }) => {
  await loginAsOutsider(page)
  const ids = await joinable(page)
  await clientNavigate(page, `/groupbuys/${ids[1]}`)
  await mock(page, (m) => void (m.priceChangeTo = 9900))
  await page.getByRole('button', { name: '참여하기' }).click()
  await page.getByLabel('수량').fill('1')
  await page.getByRole('button', { name: '참여 신청' }).click()
  await expect(page.getByText(/개당 가격이 바뀌었어요\(9,900원\)/)).toBeVisible()
  await expect(page.getByRole('button', { name: '참여 신청' })).toBeDisabled()
  await page.getByRole('button', { name: '바뀐 가격 확인했어요' }).click()
  await page.getByRole('button', { name: '참여 신청' }).click()
  await expect(page.getByText('내 참여: 1개 · 9,900원')).toBeVisible()
})
