import { expect, test, type Page } from '@playwright/test'

// 목 모드 기준. 010 탈퇴: 키보드만으로 대화상자를 다루고(포커스 이동·복귀), 막히는 경우와 성공을 확인한다.
// 목 상태는 메모리라 로그인 뒤에는 앱 안에서(popstate) 이동한다.
const clientNavigate = (page: Page, url: string) =>
  page.evaluate((u) => {
    history.pushState({}, '', u)
    dispatchEvent(new PopStateEvent('popstate'))
  }, url)

async function login(page: Page, email: string) {
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill(email)
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
}

test('진행 중 공구가 있으면 막히고 공구 링크와 안내가 보인다(키보드만)', async ({ page }) => {
  await login(page, 'organizer@school.ac.kr')
  await clientNavigate(page, '/me')
  const open = page.getByRole('region', { name: '탈퇴' }).getByRole('button', { name: '탈퇴' })
  await open.focus()
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog', { name: '탈퇴할까요?' })
  await expect(dialog.getByLabel('비밀번호')).toBeFocused()
  await page.keyboard.type('password1234')
  await page.keyboard.press('Enter')
  const alert = page.getByRole('alert', { name: '진행 중인 공구가 있어 탈퇴할 수 없어요.' })
  await expect(alert).toBeVisible()
  await expect(dialog).toHaveCount(0)
  await expect(open).toBeFocused() // 닫히면 연 버튼으로
  await expect(alert.getByRole('link').first()).toHaveAttribute('href', /\/groupbuys\/\d+$/)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('비밀번호가 틀리면 대화상자 안에 오류, 맞으면 탈퇴하고 목록에서 "탈퇴했어요", 같은 계정으로 로그인 불가', async ({ page }) => {
  await page.goto('/signup')
  await page.getByLabel('이메일', { exact: true }).fill('bye@school.ac.kr')
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByRole('heading', { name: '인증 메일을 보냈어요' })).toBeVisible()
  const token = await page.evaluate(() => (window as unknown as { __mock: { pending: { token: string }[] } }).__mock.pending[0]!.token)
  await clientNavigate(page, `/verify?token=${token}`)
  await page.getByLabel('표시 이름', { exact: true }).fill('떠날사람')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '인증 완료하기' }).click()
  await expect(page).toHaveURL(/\/$/)

  await clientNavigate(page, '/me')
  await page.getByRole('region', { name: '탈퇴' }).getByRole('button', { name: '탈퇴' }).click()
  const dialog = page.getByRole('dialog', { name: '탈퇴할까요?' })
  await dialog.getByLabel('비밀번호').fill('wrong-password')
  await dialog.getByRole('button', { name: '탈퇴하기' }).click()
  await expect(dialog.getByText('비밀번호가 맞지 않습니다.')).toBeVisible()
  await expect(dialog.getByLabel('비밀번호')).toBeFocused()
  await dialog.getByLabel('비밀번호').fill('password1234')
  await dialog.getByRole('button', { name: '탈퇴하기' }).click()
  await expect(page.getByText('탈퇴했어요.')).toBeVisible()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('link', { name: '로그인' })).toBeVisible()

  await clientNavigate(page, '/login')
  await page.getByLabel('이메일', { exact: true }).fill('bye@school.ac.kr')
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByText('이메일 또는 비밀번호가 맞지 않아요.')).toBeVisible()
})

test('T018: 안내가 길어진 대화상자도 낮은 화면(가로 폰·키보드)에서 안을 스크롤해 버튼까지 닿는다', async ({ page }) => {
  await page.setViewportSize({ width: page.viewportSize()!.width, height: 360 })
  await login(page, 'organizer@school.ac.kr')
  await clientNavigate(page, '/me')
  await page.getByRole('region', { name: '탈퇴' }).getByRole('button', { name: '탈퇴' }).click()
  const dialog = page.getByRole('dialog', { name: '탈퇴할까요?' })
  const box = (await dialog.boundingBox())!
  expect(box.y).toBeGreaterThanOrEqual(0)
  expect(box.y + box.height).toBeLessThanOrEqual(360)
  const confirm = dialog.getByRole('button', { name: '탈퇴하기' })
  await confirm.scrollIntoViewIfNeeded()
  await expect(confirm).toBeInViewport()
})
