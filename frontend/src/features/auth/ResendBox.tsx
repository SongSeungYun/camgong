import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../../shared/api/client'
import { errorMessage } from '../../shared/lib/errorMessage'
import { Button, TextField } from '../../shared/ui'
import { resend } from './api'

/** 인증 메일 재발송 진입. 로그인·인증 확인 화면에서 쓴다. */
export function ResendBox({ initialEmail = '' }: { initialEmail?: string }) {
  // 직접 고치기 전까지는 부모(로그인 화면)에서 입력 중인 이메일을 따라간다
  const [edited, setEdited] = useState<string | null>(null)
  const email = edited ?? initialEmail
  const setEmail = setEdited
  const m = useMutation({ mutationFn: () => resend(email.trim()) })
  const err = m.error instanceof ApiError ? m.error : null
  // 404는 '신청 내역 없음/이미 인증 완료'를 숨기는 응답이라 같은 문구로 안내한다
  const failText = err && (err.status === 404 ? '신청 내역이 없거나 이미 인증을 마쳤어요. 로그인하거나 가입해 주세요.' : errorMessage(err))
  return (
    <div>
      <TextField label="인증 메일 받을 이메일" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
      <Button variant="secondary" pending={m.isPending} disabled={!email.trim()} onClick={() => m.mutate()}>
        인증 메일 다시 받기
      </Button>
      {m.isSuccess && <p role="status">인증 메일을 다시 보냈어요.</p>}
      {failText && <p role="alert">{failText}</p>}
    </div>
  )
}
