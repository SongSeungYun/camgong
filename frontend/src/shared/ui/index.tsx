import { useId, type ComponentProps, type ReactNode } from 'react'
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
    <div className={s.progress} role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={max}>
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
  pending?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ open, title, message, confirmLabel = '확인', pending, onConfirm, onCancel }: ConfirmDialogProps) {
  if (!open) return null
  return (
    <div className={s.backdrop}>
      <div className={s.dialog} role="dialog" aria-modal="true" aria-label={title}>
        <h2>{title}</h2>
        {message && <p>{message}</p>}
        <div className={s.actions}>
          <Button variant="secondary" onClick={onCancel} disabled={pending}>취소</Button>
          <Button onClick={onConfirm} pending={pending}>{confirmLabel}</Button>
        </div>
      </div>
    </div>
  )
}
