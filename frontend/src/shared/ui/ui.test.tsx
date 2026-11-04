import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, test, vi } from 'vitest'
import { Badge, Button, ConfirmDialog, StateView, TextField } from '.'

// 네이티브 disabled는 포커스를 잃게 해(키보드 사용자가 body로 튕김) aria-disabled + 클릭 무시로 막는다
test('Button은 pending·disabled 동안 aria-disabled이고 포커스를 유지하며 클릭(제출 포함)을 무시한다', async () => {
  const onClick = vi.fn()
  const onSubmit = vi.fn((e: { preventDefault: () => void }) => e.preventDefault())
  const { rerender } = render(<form onSubmit={onSubmit}><Button type="submit" onClick={onClick}>저장</Button></form>)
  const btn = screen.getByRole('button', { name: '저장' }) as HTMLButtonElement
  btn.focus()
  rerender(<form onSubmit={onSubmit}><Button type="submit" pending onClick={onClick}>저장</Button></form>)
  expect(btn.disabled).toBe(false)
  expect(btn.getAttribute('aria-disabled')).toBe('true')
  expect(btn.getAttribute('aria-busy')).toBe('true')
  expect(document.activeElement).toBe(btn)
  await userEvent.click(btn)
  await userEvent.keyboard('{Enter}')
  rerender(<form onSubmit={onSubmit}><Button type="submit" disabled onClick={onClick}>저장</Button></form>)
  expect(btn.getAttribute('aria-disabled')).toBe('true')
  await userEvent.click(btn)
  expect(onClick).not.toHaveBeenCalled()
  expect(onSubmit).not.toHaveBeenCalled()
  rerender(<form onSubmit={onSubmit}><Button type="submit" onClick={onClick}>저장</Button></form>)
  expect(btn.hasAttribute('aria-disabled')).toBe(false)
  await userEvent.click(btn)
  expect(onClick).toHaveBeenCalledTimes(1)
  expect(onSubmit).toHaveBeenCalledTimes(1)
})

test('TextField는 레이블과 오류를 필드에 연결한다', () => {
  render(<TextField label="이름" error="2자 이상" />)
  const input = screen.getByLabelText('이름')
  expect(input.getAttribute('aria-invalid')).toBe('true')
  expect(document.getElementById(input.getAttribute('aria-describedby')!)!.textContent).toBe('2자 이상')
})

test('Badge는 알 수 없는 status도 일반 표시', () => {
  render(<><Badge status="OPEN" /><Badge status="WHATEVER" /></>)
  screen.getByText('모집 중')
  screen.getByText('알 수 없음')
})

test('StateView는 로딩·빈·오류(다시 시도)·정상을 구분한다', async () => {
  const retry = vi.fn()
  const { rerender } = render(<StateView state="loading" />)
  screen.getByRole('status')
  rerender(<StateView state="empty" emptyText="없어요" />)
  screen.getByText('없어요')
  rerender(<StateView state="error" onRetry={retry} />)
  await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))
  expect(retry).toHaveBeenCalled()
  rerender(<StateView state="ready">내용</StateView>)
  screen.getByText('내용')
})

test('ConfirmDialog는 열렸을 때만 보이고 확인/취소를 부른다', async () => {
  const ok = vi.fn()
  const cancel = vi.fn()
  const { rerender } = render(<ConfirmDialog open={false} title="취소할까요?" onConfirm={ok} onCancel={cancel} />)
  expect(screen.queryByRole('dialog')).toBeNull()
  rerender(<ConfirmDialog open title="취소할까요?" onConfirm={ok} onCancel={cancel} />)
  await userEvent.click(screen.getByRole('button', { name: '확인' }))
  await userEvent.click(screen.getByRole('button', { name: '취소' }))
  expect(ok).toHaveBeenCalledTimes(1)
  expect(cancel).toHaveBeenCalledTimes(1)
})

describe('ConfirmDialog 키보드·포커스 (검수 5)', () => {
  function Host({ onCancel = () => {} }: { onCancel?: () => void }) {
    const [open, setOpen] = useState(false)
    return (
      <>
        <button type="button" onClick={() => setOpen(true)}>열기</button>
        <ConfirmDialog open={open} title="정말요?" confirmLabel="네" cancelLabel="아니요" onConfirm={() => {}} onCancel={() => { onCancel(); setOpen(false) }} />
      </>
    )
  }
  test('열리면 포커스가 대화상자 안으로 가고, 닫으면 열었던 버튼으로 돌아온다', async () => {
    render(<Host />)
    const opener = screen.getByRole('button', { name: '열기' })
    await userEvent.click(opener)
    const dialog = screen.getByRole('dialog')
    expect(dialog.contains(document.activeElement)).toBe(true)
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(opener)
  })
  test('Esc는 취소와 같다', async () => {
    let cancelled = 0
    render(<Host onCancel={() => cancelled++} />)
    await userEvent.click(screen.getByRole('button', { name: '열기' }))
    await userEvent.keyboard('{Escape}')
    expect(cancelled).toBe(1)
  })
  test('Tab은 대화상자 안에서만 돈다', async () => {
    render(<Host />)
    await userEvent.click(screen.getByRole('button', { name: '열기' }))
    const dialog = screen.getByRole('dialog')
    for (let i = 0; i < 5; i++) {
      await userEvent.tab()
      expect(dialog.contains(document.activeElement)).toBe(true)
    }
    await userEvent.tab({ shift: true })
    expect(dialog.contains(document.activeElement)).toBe(true)
  })
})
