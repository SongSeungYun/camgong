import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { requestPasswordReset } from '../features/auth/api'
import { ApiError } from '../shared/api/client'
import { applyServerErrors } from '../shared/lib/formErrors'
import { errorMessage } from '../shared/lib/errorMessage'
import { remainingText } from '../shared/lib/time'
import { Button, Layout, TextField } from '../shared/ui'

/** 011 재설정 요청. 응답은 가입 여부와 무관하게 같다(안내도 "가입한 학교 메일이면"). 로그인 없이 쓴다 */
export default function PasswordResetPage() {
  const { register, handleSubmit, setError, watch, formState: { errors } } = useForm<{ email: string }>()
  const [topErrors, setTopErrors] = useState<string[]>([])
  const [sent, setSent] = useState<{ email: string; retryAt: string } | null>(null)
  const [now, setNow] = useState(Date.now())
  const waiting = !!sent && Date.parse(sent.retryAt) > now
  useEffect(() => {
    if (!waiting) return
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [waiting])

  const m = useMutation({
    mutationFn: (email: string) => requestPasswordReset(email),
    onSuccess: (r, email) => {
      setSent({ email, retryAt: r.retryAt })
      setNow(Date.now())
    },
    onError: (e) => setTopErrors(e instanceof ApiError ? applyServerErrors(e, setError, ['email']) : [errorMessage(new ApiError(0))]),
  })
  const sameEmail = waiting && watch('email')?.trim() === sent.email // 다른 주소로 고치면 바로 다시 보낼 수 있다

  return (
    <Layout>
      <h1>비밀번호 재설정</h1>
      <p>가입한 학교 이메일을 넣으면 새 비밀번호를 정하는 링크를 보내요.</p>
      <form noValidate onSubmit={handleSubmit((f) => { setTopErrors([]); setSent(null); m.mutate(f.email.trim()) })}>
        <TextField label="이메일" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        {topErrors.map((t) => <p key={t} role="alert">{t}</p>)}
        <Button type="submit" pending={m.isPending} disabled={sameEmail}>재설정 링크 받기</Button>
      </form>
      <p role="status">
        {sent && '가입한 학교 메일이면 재설정 링크를 보냈어요. 1시간 안에 열어 주세요.'}
        {sameEmail && ` ${remainingText(sent.retryAt, now)}이라 아직 다시 요청할 수 없어요.`}
      </p>
      <p><Link to="/login">로그인으로</Link></p>
    </Layout>
  )
}
