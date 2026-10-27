import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useSearchParams } from 'react-router'
import { verify } from '../features/auth/api'
import { ResendBox } from '../features/auth/ResendBox'
import { useSession } from '../features/auth/session'
import { useState } from 'react'
import { ApiError } from '../shared/api/client'
import { errorMessage } from '../shared/lib/errorMessage'
import { applyServerErrors } from '../shared/lib/formErrors'
import { Button, Layout, TextField } from '../shared/ui'

// 개인정보 수집·이용 안내문 버전. 안내문이 바뀌면 같이 올린다. (안내문 문구와 버전은 초안: 사용자가 확정한다)
const CONSENT_VERSION = 'v1'
const TOKEN_CODES = ['TOKEN_USED', 'TOKEN_EXPIRED', 'INVALID_TOKEN']
const FIELDS = ['displayName', 'password', 'consent']

interface Form {
  displayName: string
  password: string
  consent: boolean
}

/**
 * 링크를 여는 것만으로는 토큰이 소진되지 않는다(메일 스캐너 대비). 이름·비밀번호·동의를 받고 제출 버튼으로만 POST한다(변경 B2).
 * 비밀번호는 폼 상태에만 있고 주소·저장소·로그에 남기지 않는다.
 */
export default function VerifyPage() {
  const token = useSearchParams()[0].get('token') ?? ''
  const { status, setMember } = useSession()
  const { register, handleSubmit, setError, formState: { errors } } = useForm<Form>({ defaultValues: { consent: false } })
  const [topErrors, setTopErrors] = useState<string[]>([])
  const [tokenError, setTokenError] = useState<ApiError | null>(token ? null : new ApiError(400, 'INVALID_TOKEN'))

  const m = useMutation({
    mutationFn: (f: Form) => verify({ token, displayName: f.displayName.trim(), password: f.password, consent: f.consent, consentVersion: CONSENT_VERSION }),
    onSuccess: (r) => setMember(r.member),
    onError: (e) => {
      if (!(e instanceof ApiError)) return setTopErrors([errorMessage(new ApiError(0))])
      if (e.code && TOKEN_CODES.includes(e.code)) return setTokenError(e)
      if (e.code === 'DISPLAY_NAME_TAKEN') return setError('displayName', { type: 'server', message: errorMessage(e) })
      setTopErrors(applyServerErrors(e, setError, FIELDS))
    },
  })

  if (status === 'authenticated') return <Navigate to="/" replace />

  if (tokenError) {
    return (
      <Layout>
        <h1>이메일 인증</h1>
        <p role="alert">{errorMessage(tokenError)}</p>
        <p><Link to="/signup">가입 화면으로</Link></p>
        {tokenError.code === 'TOKEN_EXPIRED' && <ResendBox />}
      </Layout>
    )
  }

  return (
    <Layout>
      <h1>이메일 인증</h1>
      <p>사용할 이름과 비밀번호를 정해 가입을 마쳐요.</p>
      <form noValidate onSubmit={handleSubmit((f) => { setTopErrors([]); m.mutate(f) })}>
        <TextField label="표시 이름" autoComplete="nickname" error={errors.displayName?.message} {...register('displayName')} />
        <p>2~20자. 다른 사람에게 보이는 이름이에요.</p>
        <TextField label="비밀번호" type="password" autoComplete="new-password" error={errors.password?.message} {...register('password')} />
        <p>8자 이상(72바이트 이하).</p>
        <label>
          <input type="checkbox" {...register('consent')} /> 개인정보 수집·이용에 동의해요.
        </label>
        <details>
          <summary>안내문 보기</summary>
          <p>수집하는 항목: 이메일, 표시 이름, 비밀번호(암호화해 저장해요). 이용 목적: 가입 확인과 공동구매 서비스 이용. 보관: 탈퇴할 때까지.</p>
        </details>
        {errors.consent?.message && <p role="alert">{errors.consent.message}</p>}
        {topErrors.map((t) => <p key={t} role="alert">{t}</p>)}
        <Button type="submit" pending={m.isPending}>인증 완료하기</Button>
      </form>
    </Layout>
  )
}
