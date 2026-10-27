import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { resend, signup, type Accepted } from '../features/auth/api'
import { ApiError } from '../shared/api/client'
import { applyServerErrors } from '../shared/lib/formErrors'
import { errorMessage } from '../shared/lib/errorMessage'
import { remainingText } from '../shared/lib/time'
import { Button, Layout, TextField } from '../shared/ui'

interface Form {
  email: string
}

export default function SignupPage() {
  const { register, handleSubmit, setError, formState: { errors } } = useForm<Form>()
  const [topErrors, setTopErrors] = useState<string[]>([])
  const [accepted, setAccepted] = useState<Accepted | null>(null)
  const [already, setAlready] = useState(false)

  const m = useMutation({
    mutationFn: (f: Form) => signup(f.email.trim()),
    onSuccess: setAccepted,
    onError: (e) => {
      setAlready(e instanceof ApiError && e.code === 'ALREADY_REGISTERED')
      setTopErrors(e instanceof ApiError ? applyServerErrors(e, setError, ['email']) : [errorMessage(new ApiError(0))])
    },
  })

  if (accepted) return <Sent accepted={accepted} onAccepted={setAccepted} />

  return (
    <Layout>
      <h1>가입</h1>
      <form noValidate onSubmit={handleSubmit((f) => { setTopErrors([]); m.mutate(f) })}>
        <TextField label="이메일" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <p>학교 이메일만 가입할 수 있어요.</p>
        <p>이름과 비밀번호는 메일의 링크를 열었을 때 정해요.</p>
        {topErrors.map((t) => <p key={t} role="alert">{t}</p>)}
        {already && <p><Link to="/login">로그인하러 가기</Link></p>}
        <Button type="submit" pending={m.isPending}>가입하기</Button>
      </form>
      <p>이미 계정이 있나요? <Link to="/login">로그인</Link></p>
    </Layout>
  )
}

function Sent({ accepted, onAccepted }: { accepted: Accepted; onAccepted: (a: Accepted) => void }) {
  const [now, setNow] = useState(Date.now())
  const waiting = Date.parse(accepted.resendAvailableAt) > now
  useEffect(() => {
    if (!waiting) return
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [waiting])
  const m = useMutation({ mutationFn: () => resend(accepted.email), onSuccess: (a) => { onAccepted(a); setNow(Date.now()) } })
  return (
    <Layout>
      <h1>인증 메일을 보냈어요</h1>
      <p>{accepted.email}로 인증 메일을 보냈어요. 메일의 링크를 열어 인증을 마쳐 주세요.</p>
      <Button variant="secondary" pending={m.isPending} disabled={waiting} onClick={() => m.mutate()}>인증 메일 다시 보내기</Button>
      {waiting && <p>{remainingText(accepted.resendAvailableAt, now)}이라 아직 다시 보낼 수 없어요. 조금 뒤에 다시 보낼 수 있어요.</p>}
      {m.error instanceof ApiError && <p role="alert">{errorMessage(m.error, { nowMs: now })}</p>}
    </Layout>
  )
}
