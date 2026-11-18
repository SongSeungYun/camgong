import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { server } from '../../../mocks/server'
import { mockState } from '../../../mocks/state'
import { renderApp } from '../../../test/utils'

// 009 T014. 시드: school.ac.kr 공구 대부분, 47·48 snu.ac.kr(모집 중은 48 하나), 49·50 snue.ac.kr
const combo = () => screen.getByRole('combobox', { name: '학교 검색' })
const cardSchools = () => screen.getAllByRole('listitem').map((li) => within(li).getByText(/^학교 /).textContent!.replace('학교 ', ''))

describe('학교 범위 목록 (009)', () => {
  test('비로그인 목록 카드에 공구의 학교가 보인다(전국)', async () => {
    renderApp('/')
    await screen.findAllByRole('listitem')
    expect(new Set(cardSchools()).size).toBeGreaterThan(1)
  })

  test('학교 검색: 입력하면 후보가 뜨고 키보드로 골라 목록을 거르며 주소에 남는다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snu')
    const box = await screen.findByRole('listbox', { name: '학교 후보' })
    expect(within(box).getAllByRole('option').map((o) => o.textContent)).toEqual(['snu.ac.kr', 'snue.ac.kr'])
    expect(combo().getAttribute('aria-expanded')).toBe('true')
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{ArrowUp}')
    expect(combo().getAttribute('aria-activedescendant')).toBe(within(box).getAllByRole('option')[0]!.id)
    expect(within(box).getAllByRole('option')[0]!.getAttribute('aria-selected')).toBe('true')
    await userEvent.keyboard('{Enter}')
    await waitFor(() => expect(router.state.location.search).toContain('school=snu.ac.kr'))
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(document.activeElement).toBe(combo())
    screen.getByText('선택한 학교: snu.ac.kr')
    await waitFor(() => expect(cardSchools()).toEqual(['snu.ac.kr']))
  })

  test('마우스로 후보를 눌러도 고를 수 있고, 학교 선택 해제로 전국으로 돌아간다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snue')
    await userEvent.click(await screen.findByRole('option', { name: 'snue.ac.kr' }))
    await waitFor(() => expect(router.state.location.search).toContain('school=snue.ac.kr'))
    await userEvent.click(screen.getByRole('button', { name: '학교 선택 해제' }))
    await waitFor(() => expect(router.state.location.search).not.toContain('school='))
    expect(screen.queryByText(/선택한 학교/)).toBeNull()
  })

  test('Esc는 후보를 닫고 포커스는 입력칸에 남는다, 고르기 전에는 목록을 거르지 않는다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snu')
    await screen.findByRole('listbox')
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(combo().getAttribute('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(combo())
    expect(router.state.location.search).not.toContain('school=')
  })

  test('맞는 학교가 없으면 안내한다', async () => {
    renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'zzz')
    await screen.findByText('맞는 학교가 없어요.')
  })

  test('학교 후보를 불러오지 못하면 안내한다', async () => {
    server.use(http.get('/api/schools', () => new HttpResponse(null, { status: 500 })))
    renderApp('/')
    await screen.findAllByRole('listitem')
    await userEvent.type(combo(), 'snu')
    await screen.findByText('학교 후보를 불러오지 못했어요. 다시 입력해 주세요.')
  })

  test('주소의 school은 새로고침해도 유지되고 그 학교만 보인다', async () => {
    let sent: string | null = null
    server.use(http.get('/api/groupbuys', ({ request }) => void (sent = new URL(request.url).searchParams.get('school'))))
    renderApp('/?school=snu.ac.kr')
    await screen.findByText('선택한 학교: snu.ac.kr')
    await waitFor(() => expect(sent).toBe('snu.ac.kr'))
    await waitFor(() => expect(cardSchools()).toEqual(['snu.ac.kr']))
  })

  test('학교를 고른 채 결과가 없으면 "검색 결과 없음" 문구', async () => {
    renderApp('/?school=snu.ac.kr&category=STATIONERY')
    await screen.findByText('검색 결과가 없어요. 다른 검색어나 조건을 써보세요.')
  })

  test('로그인하면 학교 검색이 없고 "우리 학교" 머리말, 주소의 school은 보내지 않는다', async () => {
    mockState.sessionMemberId = 1
    let sent: string | null = 'unset'
    server.use(http.get('/api/groupbuys', ({ request }) => void (sent = new URL(request.url).searchParams.get('school'))))
    renderApp('/?school=snu.ac.kr')
    await screen.findByText('우리 학교 · school.ac.kr')
    expect(screen.queryByRole('combobox', { name: '학교 검색' })).toBeNull()
    expect(screen.queryByText(/선택한 학교/)).toBeNull()
    await waitFor(() => expect(sent).toBeNull())
  })

  test('비로그인으로 본 전국 목록 캐시가 로그인 뒤 목록에 섞이지 않는다', async () => {
    const { router } = renderApp('/')
    await screen.findAllByRole('listitem')
    expect(cardSchools()).toContain('snu.ac.kr')
    await router.navigate('/login')
    await userEvent.type(await screen.findByLabelText('이메일'), 'organizer@school.ac.kr')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password1234')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))
    await screen.findByText('우리 학교 · school.ac.kr')
    await waitFor(() => expect(new Set(cardSchools())).toEqual(new Set(['school.ac.kr'])))
  })
})
