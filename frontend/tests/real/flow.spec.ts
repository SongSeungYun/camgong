import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'

// 실제 백엔드 기준 통합 확인(목 아님). 메일은 안 나가므로 백엔드 로그의 verification link에서 토큰을 읽는다.
const LOG = process.env.BACKEND_LOG!
const SHOTS = process.env.SHOTS_DIR ?? '/tmp'
const tag = Date.now().toString().slice(-6)
const token = () => /token=([^\s&]+)/.exec(readFileSync(LOG, 'utf8').split('\n').filter((l) => l.includes('verification link=')).pop()!)![1]!
const noHScroll = (page: Page) => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)

async function register(page: Page, email: string, name: string) {
  await page.goto('/signup')
  await page.getByLabel('이메일', { exact: true }).fill(email)
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByRole('heading', { name: '인증 메일을 보냈어요' })).toBeVisible()
  await page.waitForTimeout(1500)
  await page.goto(`/verify?token=${token()}`)
  await page.getByLabel('표시 이름', { exact: true }).fill(name)
  await page.getByLabel('비밀번호').fill('password1234')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '인증 완료하기' }).click()
  await expect(page).toHaveURL(/\/$/)
}

const deadline = (days: number) => new Date(Date.now() + days * 86_400_000 + 9 * 3_600_000).toISOString().slice(0, 10) + 'T18:00:00+09:00'
const body = (title: string, total = 10, mine = 3) => ({
  title, description: null, category: 'FOOD', itemName: '햇반', itemUrl: 'https://example.com/i', unitPrice: 1200,
  totalQuantity: total, organizerQuantity: mine, deadline: deadline(3), openChatUrl: 'https://open.kakao.com/o/x',
})
const create = async (api: APIRequestContext, b: ReturnType<typeof body>) => ((await (await api.post('/api/groupbuys', { data: b })).json()) as { id: number }).id

test('실서버: 가입 → 개설 → 참여 → 체크표 → 알림, 취소·수정·페이지네이션', async ({ browser, page }, info) => {
  const shot = (p: Page, n: string) => p.screenshot({ path: `${SHOTS}/${info.project.name}-${n}.png`, fullPage: true })
  await register(page, `o${tag}${info.project.name.slice(-3)}@school.ac.kr`, `개설${tag}`)

  // 폼: 틀린 값 → 필드별 오류(서버 400 포함) → 입력값 유지
  await page.getByRole('link', { name: '공구 열기' }).click()
  await page.getByLabel('제목').fill('UI 통합 공구')
  await page.getByLabel('카테고리').selectOption('FOOD')
  await page.getByLabel('상품명').fill('햇반')
  await page.getByLabel('구매처 링크').fill('http://insecure.example.com')
  await page.getByLabel('개당 가격').fill('0')
  await page.getByLabel('총 수량').fill('10')
  await page.getByLabel('내 수량').fill('30')
  await page.getByLabel('마감 (한국 시각)').fill(deadline(3).slice(0, 16))
  await page.getByRole('button', { name: '공구 열기' }).click() // 오픈채팅 비움: 서버 필수 오류가 필드 옆에 나오는지
  await expect(page.getByLabel('오픈채팅 링크')).toHaveAttribute('aria-invalid', 'true')
  await shot(page, '1a-server-field-error')
  await page.getByLabel('오픈채팅 링크').fill('https://open.kakao.com/o/x')
  await page.getByRole('button', { name: '공구 열기' }).click()
  await expect(page.getByLabel('개당 가격')).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByLabel('제목')).toHaveValue('UI 통합 공구')
  await shot(page, '1-form-errors')
  await page.getByLabel('구매처 링크').fill('https://example.com/i')
  await page.getByLabel('개당 가격').fill('1200')
  await page.getByLabel('내 수량').fill('3')
  await page.getByRole('button', { name: '공구 열기' }).click()
  await expect(page).toHaveURL(/\/groupbuys\/\d+$/)
  const id = Number(/(\d+)$/.exec(page.url())![1])
  await expect(page.getByRole('heading', { name: 'UI 통합 공구' })).toBeVisible()
  await expect(page.getByText(/2026-\d\d-\d\d 18:00/)).toBeVisible() // +09:00 마감이 KST 18:00으로
  expect(await noHScroll(page)).toBe(true)
  await shot(page, '2-detail-organizer')

  // 참여자: 다른 컨텍스트
  const ctx = await browser.newContext({ viewport: info.project.use.viewport, extraHTTPHeaders: { Origin: 'http://localhost:5173' } })
  const p2 = await ctx.newPage()
  await register(p2, `p${tag}${info.project.name.slice(-3)}@school.ac.kr`, `참여${tag}`)
  await p2.goto(`/groupbuys/${id}`)
  await p2.getByRole('button', { name: '참여하기' }).click()
  await p2.getByLabel('수량').fill('99')
  await expect(p2.getByRole('button', { name: '참여 신청' })).toBeDisabled() // 남은 수량 초과는 클라이언트에서 막힘
  await p2.getByLabel('수량').fill('2')
  await p2.getByRole('button', { name: '참여 신청' }).click()
  await expect(p2.getByText(/내 참여: 2개/)).toBeVisible()
  await p2.getByRole('button', { name: '수량 변경' }).click()
  await p2.getByLabel('수량').fill('7') // 마지막 수량 → 모집 완료
  await p2.getByRole('button', { name: '수량 변경 저장' }).click()
  await expect(p2.getByText('모집 완료')).toBeVisible()
  await expect(p2.getByRole('link', { name: /오픈채팅 입장/ })).toBeVisible()
  await expect(p2.getByText(/입금 확인 전/)).toBeVisible()
  await shot(p2, '3-detail-participant-completed')

  // 개설자: 체크표
  await page.goto(`/groupbuys/${id}`)
  await page.getByRole('link', { name: '참여자 체크' }).click()
  const row = page.getByRole('listitem', { name: `참여${tag}` })
  await row.getByRole('button', { name: '입금 확인' }).click()
  await expect(row.getByText(/입금 확인됨/)).toBeVisible()
  await row.getByRole('button', { name: '수령 확인' }).click()
  await expect(row.getByText(/수령 확인됨/)).toBeVisible()
  await page.getByRole('button', { name: '미입금' }).click()
  await expect(page.getByRole('listitem', { name: `참여${tag}` })).toHaveCount(0)
  expect(await noHScroll(page)).toBe(true)
  await shot(page, '4-check')

  // 알림: 26건 이상 만들고(API) 페이지네이션
  const orgApi = page.context().request
  const parApi = ctx.request
  for (let i = 0; i < 26; i++) {
    const gid = await create(orgApi, body(`알림용 ${i}`, 2, 1))
    await parApi.post(`/api/groupbuys/${gid}/participations`, { data: { quantity: 1, expectedUnitPrice: 1200 } })
  }
  // 목록 cursor 2페이지용 모집 중 공구 22개
  for (let i = 0; i < 22; i++) await create(orgApi, body(`목록용 ${i}`))

  await page.goto('/notifications')
  await expect(page.getByRole('heading', { name: '알림' })).toBeVisible()
  const items = page.getByRole('listitem').filter({ has: page.getByRole('button') })
  await expect(items).toHaveCount(20)
  await page.getByRole('button', { name: '더 보기' }).click()
  await expect(items).toHaveCount(27) // 완료 알림 26 + 첫 공구 1
  await expect(page.getByRole('button', { name: '더 보기' })).toHaveCount(0)
  await shot(page, '5-notifications')
  await items.first().getByRole('button').click()
  await expect(page).toHaveURL(/\/groupbuys\/\d+$/)

  // 목록 cursor 2페이지
  await page.goto('/')
  await expect(page.getByRole('listitem').filter({ has: page.getByRole('link') })).toHaveCount(20)
  await page.getByRole('button', { name: '더 보기' }).click()
  // 로컬 DB에 이전 실행분이 쌓여 있어 정확한 개수 대신 '두 번째 쪽이 이어 붙었는지'를 본다
  await expect.poll(() => page.getByRole('listitem').filter({ has: page.getByRole('link') }).count()).toBeGreaterThan(20)
  const hrefs = await page.getByRole('listitem').getByRole('link').evaluateAll((a) => a.map((x) => (x as HTMLAnchorElement).getAttribute('href')))
  expect(new Set(hrefs).size).toBe(hrefs.length) // 쪽 사이에 중복 없음
  await shot(page, '6-list')

  // 수정 성공 + 취소 성공(개설자, 모집 중 공구)
  const gid2 = await create(orgApi, body('수정취소용'))
  await page.goto(`/groupbuys/${gid2}/edit`)
  await page.getByLabel('설명').fill('수정한 설명')
  await page.getByLabel('총 수량').fill('12')
  await page.getByRole('button', { name: '수정 저장' }).click()
  await expect(page).toHaveURL(new RegExp(`/groupbuys/${gid2}$`))
  await expect(page.getByText('수정한 설명')).toBeVisible()
  await page.getByRole('button', { name: '공구 취소', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await shot(page, '7-cancel-dialog')
  await page.getByRole('button', { name: '공구 취소하기' }).click()
  await expect(page).toHaveURL(/\/$/)
  await page.goto(`/groupbuys/${gid2}`)
  await expect(page.getByText('찾을 수 없는 공구예요.')).toBeVisible()

  // 완료된 공구 수정 시도 → NOT_MODIFIABLE 문구
  await page.goto(`/groupbuys/${id}/edit`)
  await expect(page.getByText('이미 모집이 끝났거나 마감이 지나서 수정·취소할 수 없어요.')).toBeVisible() // 수정 폼 없이 안내
  await shot(page, '8-not-modifiable')
  await ctx.close()
})

// API로 가입(화면 확인이 목적이 아닌 준비용)
async function apiRegister(api: APIRequestContext, email: string, name: string) {
  await api.post('/api/auth/signup', { data: { email } })
  await new Promise((r) => setTimeout(r, 1500))
  const res = await api.post('/api/auth/verify', { data: { token: token(), displayName: name, password: 'password1234', consent: true, consentVersion: 'v1' } })
  expect(res.status()).toBe(200)
}
const newUser = async (browser: import('@playwright/test').Browser, vp: { width: number; height: number } | null | undefined, who: string, n: string) => {
  const ctx = await browser.newContext({ viewport: vp, extraHTTPHeaders: { Origin: 'http://localhost:5173' } })
  await apiRegister(ctx.request, `${who}${tag}${n}@school.ac.kr`, `${who}${tag}${n}`)
  return { ctx, page: await ctx.newPage() }
}
const sql = (q: string) => execSync(`docker exec camgong-mysql-local mysql -uroot -ppw camgong -e "${q}"`, { stdio: 'pipe' })

test('실서버: 다른 출처로 위장한 쓰기(FORBIDDEN_ORIGIN)는 일반 오류 문구', async ({ browser }, info) => {
  const n = info.project.name.slice(-3) + 'f'
  const o = await newUser(browser, info.project.use.viewport, 'fo', n)
  const p = await newUser(browser, info.project.use.viewport, 'fp', n)
  const id = await create(o.ctx.request, body('출처 시험', 10, 3))
  await p.page.goto(`/groupbuys/${id}`)
  // 브라우저는 Origin을 바꿀 수 없어 서버의 실제 403 본문(위 API 확인)을 그대로 돌려주는 방식으로 화면 처리만 본다
  await p.page.route('**/api/groupbuys/*/participations', (r) =>
    r.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ detail: '허용되지 않은 출처의 요청입니다.', errors: [], title: 'FORBIDDEN_ORIGIN', type: 'about:blank', status: 403 }) }),
  )
  await p.page.getByRole('button', { name: '참여하기' }).click()
  await p.page.getByLabel('수량').fill('2')
  await p.page.getByRole('button', { name: '참여 신청' }).click()
  await expect(p.page.getByText(/잠시 문제가 생겼어요|오류/).first()).toBeVisible()
  await p.page.screenshot({ path: `${SHOTS}/${info.project.name}-9-forbidden-origin.png`, fullPage: true })
  await expect(p.page.getByRole('button', { name: '참여 신청' })).toBeEnabled() // 화면이 멈추지 않고 입력값 유지
  await expect(p.page.getByLabel('수량')).toHaveValue('2')
  await o.ctx.close(); await p.ctx.close()
})

test('실서버: 마감 경과(PENDING_CLOSE → FAILED)와 수정·취소 알림', async ({ browser }, info) => {
  test.setTimeout(180_000)
  const n = info.project.name.slice(-3) + 't'
  const o = await newUser(browser, info.project.use.viewport, 'to', n)
  const p = await newUser(browser, info.project.use.viewport, 'tp', n)
  const shot = (pg: Page, name: string) => pg.screenshot({ path: `${SHOTS}/${info.project.name}-${name}.png`, fullPage: true })

  // 수정·취소 알림(참여자에게만)
  const g1 = await create(o.ctx.request, body('알림 시험 수정취소', 10, 3))
  await p.ctx.request.post(`/api/groupbuys/${g1}/participations`, { data: { quantity: 2, expectedUnitPrice: 1200 } })
  expect((await o.ctx.request.put(`/api/groupbuys/${g1}`, { data: { ...body('알림 시험 수정취소', 10, 3), description: '바뀐 설명', unitPrice: 1200 } })).status()).toBe(200)
  expect((await o.ctx.request.post(`/api/groupbuys/${g1}/cancel`)).status()).toBe(204)

  // 마감 경과
  const g2 = await create(o.ctx.request, body('마감 시험', 10, 3))
  await p.ctx.request.post(`/api/groupbuys/${g2}/participations`, { data: { quantity: 2, expectedUnitPrice: 1200 } })
  sql(`UPDATE group_buy SET deadline = UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE WHERE id=${g2}`)
  await o.page.goto(`/groupbuys/${g2}`)
  await expect(o.page.getByText('마감됨(확정 대기)')).toBeVisible() // 스케줄러가 확정하기 전 한 번 보일 수 있다(최대 ~31초)
  await shot(o.page, '10-pending-close')
  await expect.poll(async () => { await o.page.reload(); await o.page.getByRole('heading', { level: 1 }).waitFor(); return o.page.getByText('모집 실패').count() }, { timeout: 60_000, intervals: [3000] }).toBeGreaterThan(0)
  await shot(o.page, '11-failed')
  expect(await noHScroll(o.page)).toBe(true)

  // 참여자 알림함: 모집 실패 / 취소(이동 없음) / 수정(바뀐 항목)
  await p.page.goto('/notifications')
  await expect(p.page.getByText('「마감 시험」 모집이 실패했어요.')).toBeVisible()
  await expect(p.page.getByText('바뀐 항목: 설명')).toBeVisible()
  await shot(p.page, '12-notifications-kinds')
  const cancelled = p.page.getByRole('button', { name: /알림 시험 수정취소.*공구가 취소됐어요/ })
  await cancelled.click()
  await expect(p.page).toHaveURL(/\/notifications$/) // linkable:false → 이동 안 함
  await p.page.getByRole('button', { name: /마감 시험.*모집이 실패했어요/ }).click()
  await expect(p.page).toHaveURL(new RegExp(`/groupbuys/${g2}$`))
  await o.ctx.close(); await p.ctx.close()
})
