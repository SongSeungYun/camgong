import { screen, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { mockState } from '../mocks/state'
import { findPlaceholders } from '../../scripts/check-placeholders'
import { docBody, plain } from '../test/docText'
import { renderApp } from '../test/utils'

// 개인정보 처리방침(docs/privacy-policy-v1.md 두 구분선 사이)을 /privacy에 그대로 보여준다
describe('개인정보 처리방침', () => {
  test('로그인 없이 /privacy에서 문서 본문을 그대로 보여준다(표 포함)', async () => {
    renderApp('/privacy')
    const h1 = await screen.findByRole('heading', { name: '캠공 개인정보 처리방침', level: 1 })
    const article = h1.closest('article')!
    expect(article.textContent!.replace(/\s+/g, '')).toBe(plain(docBody('privacy-policy-v1.md')))
    expect(within(article).getAllByRole('table').length).toBe(5)
    within(article).getByRole('heading', { name: '6. 국외 이전', level: 2 })
  })

  test.each(['/', '/login', '/signup', '/privacy', '/없는-주소'])('모든 화면 아래(%s)에 처리방침 링크가 있다', async (path) => {
    renderApp(path)
    const footer = await screen.findByRole('contentinfo')
    expect(within(footer).getByRole('link', { name: '개인정보 처리방침' }).getAttribute('href')).toBe('/privacy')
  })

  test('로그인한 화면 아래에도 있다', async () => {
    mockState.sessionMemberId = 1
    renderApp('/me')
    await screen.findByRole('region', { name: '탈퇴' })
    expect(within(screen.getByRole('contentinfo')).getByRole('link', { name: '개인정보 처리방침' }).getAttribute('href')).toBe('/privacy')
  })
})

describe('배포 빌드의 자리표시 검사', () => {
  test('대괄호로 싼 한글 자리표시를 찾는다', () => {
    expect(findPlaceholders('성명 <td>[보호책임자 실명]</td> 이 처리방침은 [시행일]부터')).toEqual(['[보호책임자 실명]', '[시행일]'])
    expect(findPlaceholders("{'[배포 대상 확정 뒤: Amazon Web Services Korea LLC]'}")).toEqual(['[배포 대상 확정 뒤: Amazon Web Services Korea LLC]'])
  })

  test('코드의 대괄호(배열·타입·구조 분해)는 자리표시가 아니다', () => {
    expect(findPlaceholders("const ROWS: [string, string][] = [['학교 이메일', '로그인']]\nROWS.map(([item, purpose]) => item)")).toEqual([])
  })
})
