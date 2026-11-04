import { expect, test, type Page } from '@playwright/test'

// 목 모드 기준. 키보드만으로 확인 대화상자를 다룰 때의 포커스(실제 브라우저, jsdom 아님).
// 목 상태는 메모리라 로그인 후에는 앱 안에서 이동한다.
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

// 시드 6번: 모집 중, 개설자 1(organizer@), 참여자 2(participant@)
test('공구 취소 대화상자: 열면 돌아가기에 포커스, Tab은 안에서만 돌고, Esc로 닫으면 연 버튼으로 돌아온다', async ({ page }) => {
  await login(page, 'organizer@school.ac.kr')
  await clientNavigate(page, '/groupbuys/6')
  const opener = page.getByRole('button', { name: '공구 취소', exact: true })
  await opener.focus()
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog', { name: '공구를 취소할까요?' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('button', { name: '돌아가기' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(dialog.getByRole('button', { name: '공구 취소하기' })).toBeFocused()
  await page.keyboard.press('Tab') // 마지막에서 Tab → 처음으로
  await expect(dialog.getByRole('button', { name: '돌아가기' })).toBeFocused()
  await page.keyboard.press('Shift+Tab') // 처음에서 Shift+Tab → 마지막으로
  await expect(dialog.getByRole('button', { name: '공구 취소하기' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(opener).toBeFocused()
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible() // 취소되지 않았다
})

test('돌아가기를 Enter로 누르면 닫히고 포커스가 연 버튼으로 돌아온다', async ({ page }) => {
  await login(page, 'organizer@school.ac.kr')
  await clientNavigate(page, '/groupbuys/6')
  const opener = page.getByRole('button', { name: '공구 취소', exact: true })
  await opener.focus()
  await page.keyboard.press('Enter')
  await page.keyboard.press('Enter') // 첫 포커스가 돌아가기라 실수로 Enter를 눌러도 취소되지 않는다
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(opener).toBeFocused()
})

test('참여 취소 성공으로 연 버튼이 사라지면 포커스는 본문(main)으로 간다', async ({ page }) => {
  await login(page, 'participant@school.ac.kr')
  await clientNavigate(page, '/groupbuys/6')
  await page.getByRole('button', { name: '참여 취소', exact: true }).focus()
  await page.keyboard.press('Enter')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: '참여 취소하기' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('button', { name: '참여하기' })).toBeVisible()
  await expect(page.getByRole('main')).toBeFocused()
})

test('서버 필드 오류 뒤 포커스는 화면 순서상 첫 오류 필드로 간다', async ({ page }) => {
  await login(page, 'organizer@school.ac.kr')
  await page.getByRole('link', { name: '공구 열기' }).click()
  const kst = new Date(Date.now() + 2 * 86_400_000 + 9 * 3_600_000).toISOString().slice(0, 16)
  await page.getByLabel('제목').fill('포커스 시험')
  await page.getByLabel('카테고리').selectOption('FOOD')
  await page.getByLabel('상품명').fill('상품')
  await page.getByLabel('구매처 링크').fill('http://insecure.example.com')
  await page.getByLabel('개당 가격').fill('1200')
  await page.getByLabel('총 수량').fill('10')
  await page.getByLabel('내 수량').fill('3')
  await page.getByLabel('마감 (한국 시각)').fill(kst)
  await page.getByLabel('오픈채팅 링크').fill('https://open.kakao.com/o/x')
  await page.getByRole('button', { name: '공구 열기' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByLabel('구매처 링크')).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByLabel('구매처 링크')).toBeFocused() // 처리 중 비활성화된 제출 버튼에서 body로 날아가지 않는다
})

// 처리 중 버튼은 aria-disabled라 포커스를 잃지 않는다(네이티브 disabled면 body로 튕김)
test('참여: 가격 변경(PRICE_CHANGED) 거부 뒤에도 키보드 포커스가 폼 안에 남고, 재확인 뒤 제출 버튼으로 간다', async ({ page }) => {
  await login(page, 'outsider@school.ac.kr')
  await clientNavigate(page, '/groupbuys/6')
  await page.evaluate(() => void ((window as unknown as { __mock: { priceChangeTo: number | null } }).__mock.priceChangeTo = 9900))
  await page.getByRole('button', { name: '참여하기' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByLabel('수량')).toBeFocused() // 폼을 열면 수량 입력으로
  await page.keyboard.type('1')
  await page.keyboard.press('Enter') // 입력칸에서 Enter로 제출
  await expect(page.getByText(/개당 가격이 바뀌었어요\(9,900원\)/)).toBeVisible()
  const submit = page.getByRole('button', { name: '참여 신청' })
  await expect(submit).toBeDisabled() // aria-disabled
  expect(await page.evaluate(() => document.activeElement === document.body)).toBe(false)
  await page.getByRole('button', { name: '바뀐 가격 확인했어요' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('button', { name: '바뀐 가격 확인했어요' })).toHaveCount(0)
  await expect(submit).toBeFocused() // 사라진 재확인 버튼 대신 제출 버튼으로
  await page.keyboard.press('Enter')
  await expect(page.getByText('내 참여: 1개 · 9,900원')).toBeVisible()
})

// 시드 4번: 모집 완료, 개설자 2(participant@), 참여자 3(구경꾼)
test('체크표: 키보드로 입금 확인을 토글해도 포커스가 그 행 버튼에 남는다', async ({ page }) => {
  await login(page, 'participant@school.ac.kr')
  await clientNavigate(page, '/groupbuys/4/check')
  const row = page.getByRole('listitem', { name: '구경꾼' })
  await row.getByRole('button', { name: '입금 확인' }).focus()
  await page.keyboard.press('Enter')
  await expect(row.getByText(/입금 확인됨/)).toBeVisible()
  await expect(row.getByRole('button', { name: '입금 확인 취소' })).toBeFocused()
  await page.keyboard.press('Enter') // 같은 자리에서 되돌리기
  await expect(row.getByText('입금 확인 전')).toBeVisible()
  await expect(row.getByRole('button', { name: '입금 확인', exact: true })).toBeFocused()
})
