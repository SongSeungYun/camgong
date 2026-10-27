import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import { Badge, Button, ConfirmDialog, StateView, TextField } from '.'

test('Button은 pending 동안 비활성화된다', () => {
  render(<Button pending>저장</Button>)
  expect((screen.getByRole('button', { name: '저장' }) as HTMLButtonElement).disabled).toBe(true)
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
