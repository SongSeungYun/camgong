import { useMutation } from '@tanstack/react-query'
import { startTransition, useId, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../../shared/api/client'
import { errorMessage } from '../../shared/lib/errorMessage'
import { Button, ConfirmDialog, TextField } from '../../shared/ui'
import { withdraw, type WithdrawBlocker } from './api'
import { useSession } from './session'

/** 위에서부터 먼저 맞는 안내(010 plan): 마감 지난 공구는 개설자도 취소할 수 없어 확정을 기다려야 한다 */
const hint = (b: WithdrawBlocker) =>
  b.status === 'PENDING_CLOSE' ? '확정된 뒤 다시 시도하세요' : b.role === 'ORGANIZER' ? '공구를 취소하세요' : b.role === 'PARTICIPANT' ? '참여를 취소하세요' : '공구 상세에서 확인하세요'

/** 지우는 것·남는 것. docs/consent-v1.md "보관 기간"과 같은 내용이어야 한다(탈퇴 영역과 대화상자 둘 다) */
const Facts = () => (
  <>
    <p>탈퇴하면 이메일·표시 이름·비밀번호, 내 알림, 내가 연 공구의 설명을 바로 지워요. 백업과 인증 메일 발송 기록에서는 7일 안에 지워요. 지운 메일이 Google 시스템에서 완전히 사라지기까지는 더 걸릴 수 있어요.</p>
    <p>다른 참여자의 기록을 지키기 위해 함께한 공구의 수량과 입금·수령 기록, 내가 연 공구의 제목·상품명·상품 링크·오픈채팅 링크, 소속 학교, 가입·탈퇴 시각, 동의 시각·버전은 "탈퇴한 회원"으로 남고, 그 공구가 끝난 날부터 1년이 지나면 공구와 함께 지워요.</p>
  </>
)

/** 010 내 활동의 탈퇴 영역. 비밀번호를 다시 받고 되돌릴 수 없음을 확인받는다. 비밀번호는 이 컴포넌트 상태에만 있다. */
export function WithdrawSection() {
  const id = useId()
  const { forget } = useSession()
  const navigate = useNavigate()
  const pwRef = useRef<HTMLInputElement>(null)
  const openerRef = useRef<HTMLButtonElement>(null)
  const inFlight = useRef(false) // Enter 연타가 렌더 전에 두 번 보내지 않게(버튼은 pending으로 막지만 폼 제출은 아니다)
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [blockers, setBlockers] = useState<WithdrawBlocker[] | null>(null)

  const m = useMutation({
    mutationFn: (pw: string) => withdraw(pw),
    networkMode: 'always', // 오프라인이어도 멈춰 있지 않고 바로 실패로 안내한다(다시 연결될 때 확인 없이 실행되지 않게)
    gcTime: 0, // 관찰자가 떨어지면(reset) 바로 지워져 비밀번호(variables)가 캐시에 남지 않는다
    onSuccess: async () => {
      // 라우터는 이동을 트랜지션으로 그린다. 세션 비우기도 트랜지션으로 해야 같은 커밋에 반영된다
      // (급한 업데이트로 먼저 비우면 아직 /me인 화면에서 보호 라우트가 /login으로 보낸다).
      // 같이 커밋되지 않으면 만료 안내가 켜질 수 있어 Withdraw.test '리뷰 3'이 지킨다.
      await navigate('/', { replace: true, state: { notice: '탈퇴했어요.' } })
      startTransition(forget)
    },
    onError: (e) => {
      inFlight.current = false
      m.reset() // 비밀번호(variables)를 캐시에서 떼어 낸다
      const err = e instanceof ApiError ? e : new ApiError(0)
      if (err.code === 'ACTIVE_GROUPBUY') {
        setBlockers(Array.isArray(err.extra.blockers) ? (err.extra.blockers as WithdrawBlocker[]) : [])
        return close()
      }
      const pwErrors = err.fieldErrors.filter((f) => f.field === 'password').map((f) => f.message)
      if (pwErrors.length) {
        setFieldError(pwErrors.join(' / '))
        setPassword('')
        return pwRef.current?.focus()
      }
      setFormError(errorMessage(err)) // 401은 전역 처리(로그인으로), 429·5xx 등은 대화상자 안에
    },
  })

  const close = () => {
    m.reset()
    setOpen(false)
    setPassword('')
    setFieldError(null)
    setFormError(null)
  }
  const submit = () => {
    if (inFlight.current) return
    inFlight.current = true
    setFieldError(null)
    setFormError(null)
    m.mutate(password)
  }

  return (
    <section aria-labelledby={`${id}-h`}>
      <h2 id={`${id}-h`}>탈퇴</h2>
      <Facts />
      <p>탈퇴는 되돌릴 수 없어요.</p>
      <p>모집 중이거나 마감 확정을 기다리는 공구가 있으면 탈퇴할 수 없어요. 모집 중인 공구는 참여를 취소하거나 내가 연 공구를 취소하면 되고, 마감 확정을 기다리는 공구는 확정된 뒤에 할 수 있어요.</p>
      <Button ref={openerRef} variant="secondary" onClick={() => { setBlockers(null); setOpen(true) }}>탈퇴</Button>
      {blockers && (
        <div role="alert" aria-labelledby={`${id}-b`}>
          <p id={`${id}-b`}>진행 중인 공구가 있어 탈퇴할 수 없어요.</p>
          <ul>
            {blockers.map((b) => (
              <li key={b.groupBuyId}>
                <Link to={`/groupbuys/${b.groupBuyId}`}>{b.title}</Link>{' — '}{hint(b)}
              </li>
            ))}
          </ul>
        </div>
      )}
      <ConfirmDialog
        open={open}
        title="탈퇴할까요?"
        message={
          <>
            <p>되돌릴 수 없어요. 탈퇴하려면 비밀번호를 다시 입력해 주세요.</p>
            <Facts />
          </>
        }
        confirmLabel="탈퇴하기"
        cancelLabel="돌아가기"
        pending={m.isPending}
        onCancel={close}
        onConfirm={submit}
        returnFocus={openerRef}
      >
        <form noValidate onSubmit={(e) => { e.preventDefault(); submit() }}>
          <TextField
            ref={pwRef}
            label="비밀번호"
            type="password"
            autoComplete="current-password"
            value={password}
            error={fieldError ?? undefined}
            onChange={(e) => {
              setPassword(e.target.value)
              setFieldError(null) // 다시 입력하기 시작하면 이전 오류를 걷는다
            }}
          />
        </form>
        {formError && <p role="alert">{formError}</p>}
      </ConfirmDialog>
    </section>
  )
}
