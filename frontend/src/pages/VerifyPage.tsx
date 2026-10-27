import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Navigate, useSearchParams } from 'react-router'
import { verify } from '../features/auth/api'
import { ResendBox } from '../features/auth/ResendBox'
import { useSession } from '../features/auth/session'
import { ApiError } from '../shared/api/client'
import { errorMessage } from '../shared/lib/errorMessage'
import { Button, Layout, TextField } from '../shared/ui'

/** 링크를 여는 것만으로는 토큰이 소진되지 않는다(메일 스캐너 대비). 사용자의 확인 동작에 POST한다. */
export default function VerifyPage() {
  const token = useSearchParams()[0].get('token') ?? ''
  const { status, setMember } = useSession()
  const [name, setName] = useState('')
  const m = useMutation({ mutationFn: (displayName?: string) => verify(token, displayName), onSuccess: (r) => setMember(r.member) })
  const err = m.error instanceof ApiError ? m.error : null

  if (status === 'authenticated') return <Navigate to="/" replace />
  const nameTaken = err?.code === 'DISPLAY_NAME_TAKEN'

  return (
    <Layout>
      <h1>이메일 인증</h1>
      {nameTaken ? (
        <>
          <p role="alert">{errorMessage(err!)}</p>
          <TextField label="새 표시 이름" value={name} onChange={(e) => setName(e.target.value)} />
          <Button pending={m.isPending} disabled={!name.trim()} onClick={() => m.mutate(name.trim())}>이 이름으로 인증 완료하기</Button>
        </>
      ) : (
        <>
          <p>아래 버튼을 눌러 가입을 마쳐요.</p>
          <Button pending={m.isPending} onClick={() => m.mutate(undefined)}>인증 완료하기</Button>
          {err && <p role="alert">{errorMessage(err)}</p>}
          {err && <ResendBox />}
        </>
      )}
    </Layout>
  )
}
