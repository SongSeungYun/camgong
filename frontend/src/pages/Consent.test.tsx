import { screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import { docBody, plain } from '../test/docText'
import { renderApp } from '../test/utils'

// 010 T013: 인증 화면의 동의문 본문은 docs/consent-v1.md의 두 구분선(---) 사이 내용 그대로여야 한다.
const body = docBody('consent-v1.md')

test('동의문 본문을 체크박스 위 <details>("자세히 보기")에 그대로 보여주고, 연락처 자리표시·문의 문장은 없다(2026-11-19 사용자 결정)', async () => {
  renderApp('/verify?token=abc')
  const summary = await screen.findByText('자세히 보기')
  const details = summary.closest('details')!
  const shown = [...details.childNodes].filter((n) => n !== summary).map((n) => n.textContent).join('')
  expect(shown.replace(/\s+/g, '')).toBe(plain(body))
  expect(details.textContent).not.toMatch(/연락처|문의:|\[[^\]]*\]/)
  const box = screen.getByRole('checkbox', { name: '개인정보 수집·이용에 동의해요.' })
  expect(details.compareDocumentPosition(box) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  expect(screen.queryByText('안내문 보기')).toBeNull() // 예전 요약 안내는 없앤다
})

test('동의문의 "개인정보 처리방침"은 처리방침 화면 링크다', async () => {
  renderApp('/verify?token=abc')
  const summary = await screen.findByText('자세히 보기')
  const details = summary.closest('details')!
  expect(within(details).getByRole('link', { name: '개인정보 처리방침' }).getAttribute('href')).toBe('/privacy')
})
