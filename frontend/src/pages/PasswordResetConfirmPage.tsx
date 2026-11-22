import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { confirmPasswordReset } from '../features/auth/api'
import { useSession } from '../features/auth/session'
import { ApiError } from '../shared/api/client'
import { applyServerErrors } from '../shared/lib/formErrors'
import { errorMessage } from '../shared/lib/errorMessage'
import { Button, Layout, TextField } from '../shared/ui'

const TOKEN_CODES = ['TOKEN_USED', 'TOKEN_EXPIRED', 'INVALID_TOKEN']

interface Form {
  password: string
  again: string
}

/**
 * 011 새 비밀번호 정하기. 링크를 여는 것만으로는 아무 요청도 보내지 않고 제출할 때만 보낸다(인증 화면과 같다).
 * 성공하면 서버가 그 회원의 모든 세션을 끝낸다. 자동 로그인 없이 화면 세션도 비우고 로그인 화면으로 보낸다.
 */
export default function PasswordResetConfirmPage() {
  const token = useSearchParams()[0].get('token') ?? ''
  const { forget } = useSession()
  const navigate = useNavigate()
  const { register, handleSubmit, setError, formState: { errors } } = useForm<Form>()
  const [topErrors, setTopErrors] = useState<string[]>([])
  const [tokenError, setTokenError] = useState<ApiError | null>(token ? null : new ApiError(400, 'INVALID_TOKEN'))

  const m = useMutation({
    mutationFn: (password: string) => confirmPasswordReset(token, password),
    gcTime: 0, // 비밀번호(variables)가 캐시에 남지 않게
    onSuccess: () => {
      // 먼저 비운다: 로그인 상태로 /login에 가면 목록으로 돌려보낸다
      forget()
      void navigate('/login', { replace: true, state: { notice: '비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.' } })
    },
    onError: (e) => {
      if (!(e instanceof ApiError)) return setTopErrors([errorMessage(new ApiError(0))])
      if (e.code && TOKEN_CODES.includes(e.code)) return setTokenError(e)
      setTopErrors(applyServerErrors(e, setError, ['password']))
    },
  })

  if (tokenError) {
    return (
      <Layout>
        <h1>새 비밀번호 정하기</h1>
        <p role="alert">{errorMessage(tokenError)}</p>
        <p><Link to="/password-reset">다시 요청하기</Link></p>
      </Layout>
    )
  }

  return (
    <Layout>
      <h1>새 비밀번호 정하기</h1>
      <p>바꾸면 모든 기기에서 로그아웃돼요. 새 비밀번호로 다시 로그인해 주세요.</p>
      <form
        noValidate
        onSubmit={handleSubmit((f) => {
          setTopErrors([])
          if (f.password !== f.again) return setError('again', { type: 'mismatch', message: '두 비밀번호가 달라요.' })
          m.mutate(f.password)
        })}
      >
        <TextField label="새 비밀번호" type="password" autoComplete="new-password" error={errors.password?.message} {...register('password')} />
        <p>8자 이상(72바이트 이하).</p>
        <TextField label="새 비밀번호 확인" type="password" autoComplete="new-password" error={errors.again?.message} {...register('again')} />
        {topErrors.map((t) => <p key={t} role="alert">{t}</p>)}
        <Button type="submit" pending={m.isPending}>비밀번호 바꾸기</Button>
      </form>
    </Layout>
  )
}
