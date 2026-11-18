import { readFileSync } from 'node:fs'
import { screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { renderApp } from '../test/utils'

// 010 T013: 인증 화면의 동의문 본문은 docs/consent-v1.md의 두 구분선(---) 사이 내용 그대로여야 한다.
// 마크다운 표기(**, `, 표 구분, 목록 기호)를 걷어낸 글자를 공백 없이 순서대로 비교한다.
// jsdom 환경에선 import.meta.url이 file:이 아니라 쓸 수 없다. contrast.test.ts처럼 frontend/ 작업 디렉터리 기준으로 읽는다
const md = readFileSync('../docs/consent-v1.md', 'utf8')
const body = md.split(/\n---\n/)[1]!
const plain = (s: string) =>
  s
    .split('\n')
    .filter((l) => !/^\|[-\s|]+\|$/.test(l)) // 표 구분 줄
    .map((l) => l.replace(/^- /, '').replace(/\*\*|`|\|/g, ''))
    .join('')
    .replace(/\s+/g, '')

test('동의문 본문을 체크박스 위 <details>("자세히 보기")에 그대로 보여주고, 자리표시 [운영자 연락처]를 유지한다', async () => {
  renderApp('/verify?token=abc')
  const summary = await screen.findByText('자세히 보기')
  const details = summary.closest('details')!
  const shown = [...details.childNodes].filter((n) => n !== summary).map((n) => n.textContent).join('')
  expect(shown.replace(/\s+/g, '')).toBe(plain(body))
  expect(details.textContent).toContain('[운영자 연락처]')
  const box = screen.getByRole('checkbox', { name: '개인정보 수집·이용에 동의해요.' })
  expect(details.compareDocumentPosition(box) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  expect(screen.queryByText('안내문 보기')).toBeNull() // 예전 요약 안내는 없앤다
})
