import { useEffect, useId, useRef, type ComponentProps, type ReactNode } from 'react'
import { isKnownStatus, type KnownStatus } from '../api/types'
import s from './ui.module.css'

export function Layout({ children }: { children: ReactNode }) {
  return <main className={s.page}>{children}</main>
}

type ButtonProps = ComponentProps<'button'> & { variant?: 'primary' | 'secondary'; pending?: boolean }

/** pending 동안 비활성화해 중복 제출을 막는다(FR-007). */
export function Button({ variant = 'primary', pending = false, disabled, className, ...rest }: ButtonProps) {
  const cls = [s.button, variant === 'secondary' && s.secondary, className].filter(Boolean).join(' ')
  return <button type="button" {...rest} className={cls} disabled={disabled || pending} aria-busy={pending || undefined} />
}

interface FieldProps {
  label: string
  /** 필드 옆에 보이는 오류(서버 errors[].message 포함) */
  error?: string
}

function Field({ label, error, children, id }: FieldProps & { children: (a: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode; id: string }) {
  const errId = `${id}-err`
  return (
    <div className={s.field}>
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': !!error, 'aria-describedby': error ? errId : undefined })}
      {error && (
        <p id={errId} className={s.error} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

export function TextField({ label, error, ...rest }: FieldProps & ComponentProps<'input'>) {
  const id = useId()
  return <Field id={id} label={label} error={error}>{(a) => <input {...rest} {...a} />}</Field>
}

export function SelectField({ label, error, children, ...rest }: FieldProps & ComponentProps<'select'>) {
  const id = useId()
  return <Field id={id} label={label} error={error}>{(a) => <select {...rest} {...a}>{children}</select>}</Field>
}

export function TextAreaField({ label, error, ...rest }: FieldProps & ComponentProps<'textarea'>) {
  const id = useId()
  return <Field id={id} label={label} error={error}>{(a) => <textarea {...rest} {...a} />}</Field>
}

const STATUS_LABELS: Record<KnownStatus, string> = {
  OPEN: '모집 중',
  PENDING_CLOSE: '마감됨(확정 대기)',
  COMPLETED: '모집 완료',
  FAILED: '모집 실패',
}

/** 서버 status를 그대로 뱃지로. 알 수 없는 값은 일반 표시(FR-008). */
export function Badge({ status }: { status: string }) {
  return <span className={s.badge}>{isKnownStatus(status) ? STATUS_LABELS[status] : '알 수 없음'}</span>
}

export function ProgressBar({ value, max }: { value: number; max: number }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0
  return (
    <div className={s.progress} role="progressbar" aria-label={`확보 ${value}개 / 총 ${max}개`} aria-valuenow={value} aria-valuemin={0} aria-valuemax={max}>
      <div style={{ width: `${pct}%` }} />
    </div>
  )
}

interface StateViewProps {
  state: 'loading' | 'empty' | 'error' | 'ready'
  children?: ReactNode
  emptyText?: string
  errorText?: string
  onRetry?: () => void
}

/** 로딩·빈 상태·오류(다시 시도)·정상을 구분해 보여준다(FR-007). */
export function StateView({ state, children, emptyText = '표시할 내용이 없어요.', errorText = '잠시 문제가 생겼어요. 다시 시도해 주세요.', onRetry }: StateViewProps) {
  if (state === 'ready') return <>{children}</>
  if (state === 'loading') return <p className={s.state} role="status">불러오는 중…</p>
  if (state === 'empty') return <p className={s.state}>{emptyText}</p>
  return (
    <div className={s.state} role="alert">
      <p>{errorText}</p>
      {onRetry && <Button onClick={onRetry}>다시 시도</Button>}
    </div>
  )
}

interface ConfirmDialogProps {
  open: boolean
  title: string
  message?: string
  confirmLabel?: string
  cancelLabel?: string
  pending?: boolean
  onConfirm: () => void
  onCancel: () => void
}

const FOCUSABLE = 'button:not([disabled]), [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'

/** 열리면 포커스가 안으로 가고, Tab은 안에서만 돌며, Esc는 취소, 닫으면 열기 전 요소로 포커스가 돌아온다. */
export function ConfirmDialog({ open, title, message, confirmLabel = '확인', cancelLabel = '취소', pending, onConfirm, onCancel }: ConfirmDialogProps) {
  const ref = useRef<HTMLDivElement>(null)
  const cancelRef = useRef(onCancel)
  cancelRef.current = onCancel

  useEffect(() => {
    if (!open) return
    const opener = document.activeElement as HTMLElement | null
    const node = ref.current!
    const items = () => Array.from(node.querySelectorAll<HTMLElement>(FOCUSABLE))
    // 취소(첫 버튼)에 먼저 포커스: 실수로 Enter를 눌러도 되돌릴 수 없는 확인이 실행되지 않는다
    ;(items()[0] ?? node).focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        cancelRef.current()
      } else if (e.key === 'Tab') {
        const list = items()
        if (list.length === 0) return e.preventDefault()
        const first = list[0]!
        const last = list[list.length - 1]!
        if (e.shiftKey && (document.activeElement === first || !node.contains(document.activeElement))) {
          e.preventDefault()
          last.focus()
        } else if (!e.shiftKey && (document.activeElement === last || !node.contains(document.activeElement))) {
          e.preventDefault()
          first.focus()
        }
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      opener?.focus()
    }
  }, [open])

  if (!open) return null
  return (
    <div className={s.backdrop}>
      <div ref={ref} className={s.dialog} role="dialog" aria-modal="true" aria-label={title} tabIndex={-1}>
        <h2>{title}</h2>
        {message && <p>{message}</p>}
        <div className={s.actions}>
          <Button variant="secondary" onClick={onCancel} disabled={pending}>{cancelLabel}</Button>
          <Button onClick={onConfirm} pending={pending}>{confirmLabel}</Button>
        </div>
      </div>
    </div>
  )
}
