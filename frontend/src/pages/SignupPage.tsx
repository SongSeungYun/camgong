import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { resend, signup, type Accepted, type SignupBody } from '../features/auth/api'
import { ApiError } from '../shared/api/client'
import { applyServerErrors } from '../shared/lib/formErrors'
import { errorMessage } from '../shared/lib/errorMessage'
import { remainingText } from '../shared/lib/time'
import { Button, Layout, TextField } from '../shared/ui'

// 개인정보 수집·이용 안내문 버전. 안내문이 바뀌면 같이 올린다.
const CONSENT_VERSION = 'v1'
type Form = Omit<SignupBody, 'consentVersion'>
const FIELDS = ['email', 'displayName', 'password', 'consent']

export default function SignupPage() {
  const { register, handleSubmit, setError, formState: { errors } } = useForm<Form>({ defaultValues: { consent: false } })
  const [topErrors, setTopErrors] = useState<string[]>([])
  const [accepted, setAccepted] = useState<Accepted | null>(null)

  const m = useMutation({
    mutationFn: (f: Form) => signup({ ...f, email: f.email.trim(), displayName: f.displayName.trim(), consentVersion: CONSENT_VERSION }),
    onSuccess: setAccepted,
    onError: (e) => setTopErrors(e instanceof ApiError ? applyServerErrors(e, setError, FIELDS) : [errorMessage(new ApiError(0))]),
  })

  if (accepted) return <Sent accepted={accepted} onAccepted={setAccepted} />

  return (
    <Layout>
      <h1>가입</h1>
      <form noValidate onSubmit={handleSubmit((f) => { setTopErrors([]); m.mutate(f) })}>
        <TextField label="이메일" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <p>학교 이메일만 가입할 수 있어요.</p>
        <TextField label="표시 이름" autoComplete="nickname" error={errors.displayName?.message} {...register('displayName')} />
        <p>2~20자. 다른 사람에게 보이는 이름이에요.</p>
        <TextField label="비밀번호" type="password" autoComplete="new-password" error={errors.password?.message} {...register('password')} />
        <p>8자 이상(72바이트 이하).</p>
        <label>
          <input type="checkbox" {...register('consent')} /> 이메일과 표시 이름을 서비스 이용을 위해 수집·이용하는 데 동의해요.
        </label>
        {errors.consent?.message && <p role="alert">{errors.consent.message}</p>}
        {topErrors.map((t) => <p key={t} role="alert">{t}</p>)}
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
