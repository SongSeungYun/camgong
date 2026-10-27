import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, Navigate } from 'react-router'
import { login } from '../features/auth/api'
import { ResendBox } from '../features/auth/ResendBox'
import { useSession } from '../features/auth/session'
import { ApiError } from '../shared/api/client'
import { errorMessage } from '../shared/lib/errorMessage'
import { Button, Layout, TextField } from '../shared/ui'

interface Form {
  email: string
  password: string
}

export default function LoginPage() {
  const { status, expired, setMember } = useSession()
  const { register, handleSubmit, watch } = useForm<Form>()
  const m = useMutation({ mutationFn: (f: Form) => login(f.email.trim(), f.password), onSuccess: (r) => setMember(r.member) })

  if (status === 'authenticated') return <Navigate to="/" replace />
  const failText = m.error instanceof ApiError ? errorMessage(m.error) : m.error ? errorMessage(new ApiError(0)) : null

  return (
    <Layout>
      <h1>로그인</h1>
      {expired && <p role="status">로그인이 필요해요. 입력하던 내용은 저장되지 않아요.</p>}
      <form noValidate onSubmit={handleSubmit((f) => m.mutate(f))}>
        <TextField label="이메일" type="email" autoComplete="email" {...register('email')} />
        <TextField label="비밀번호" type="password" autoComplete="current-password" {...register('password')} />
        {failText && <p role="alert">{failText}</p>}
        <Button type="submit" pending={m.isPending}>로그인</Button>
      </form>
      <p>
        <Link to="/signup">가입하기</Link>
      </p>
      <section aria-label="인증 메일">
        <p>가입했는데 아직 인증 전이라면 인증 메일 다시 받기</p>
        <ResendBox initialEmail={watch('email')} />
      </section>
    </Layout>
  )
}
