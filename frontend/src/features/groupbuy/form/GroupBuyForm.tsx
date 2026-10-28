import { useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { ApiError } from '../../../shared/api/client'
import { CATEGORY_LABELS, type KnownCategory } from '../../../shared/api/types'
import { applyServerErrors } from '../../../shared/lib/formErrors'
import { errorMessage } from '../../../shared/lib/errorMessage'
import { amount, formatWon } from '../../../shared/lib/money'
import { toKstLocalInput } from '../../../shared/lib/time'
import { Button, SelectField, TextAreaField, TextField } from '../../../shared/ui'
import { EMPTY_VALUES, FORM_FIELDS, toRequestBody, type FormValues, type GroupBuyRequest } from './fieldMap'

interface Props {
  submitLabel: string
  defaultValues?: FormValues
  /** 수정 폼의 안내 문구 */
  notes?: ReactNode
  /** 수정 폼: 마감을 건드리지 않았으면 초까지 있는 원래 값을 그대로 보낸다 */
  originalDeadline?: string
  onSubmit: (body: GroupBuyRequest) => Promise<unknown>
}

/** 개설·수정 공용 폼. 범위는 안내용이고 최종 판단은 서버 응답이다(FR-024). */
export function GroupBuyForm({ submitLabel, defaultValues = EMPTY_VALUES, notes, originalDeadline, onSubmit }: Props) {
  const { register, handleSubmit, setError, watch, formState: { errors, isSubmitting } } = useForm<FormValues>({ defaultValues })
  const [topErrors, setTopErrors] = useState<string[]>([])

  const price = Number(watch('unitPrice'))
  const qty = Number(watch('organizerQuantity'))
  const preview = Number.isInteger(price) && Number.isInteger(qty) && price >= 1 && qty >= 1 ? formatWon(amount(price, qty)) : '-'

  const submit = handleSubmit(async (v) => {
    setTopErrors([])
    if (!v.deadline) return setError('deadline', { type: 'required', message: '마감 시각을 입력해 주세요' })
    try {
      const body = toRequestBody(v)
      // datetime-local은 분 단위라, 값이 처음 그대로면 초가 잘리지 않게 원래 ISO를 쓴다
      if (originalDeadline && v.deadline === toKstLocalInput(originalDeadline)) body.deadline = originalDeadline
      await onSubmit(body)
    } catch (e) {
      setTopErrors(e instanceof ApiError ? applyServerErrors(e, setError, FORM_FIELDS) : [errorMessage(new ApiError(0))])
    }
  })

  return (
    <form noValidate onSubmit={submit}>
      <TextField label="제목" error={errors.title?.message} {...register('title')} />
      <p>100자 이하</p>
      <TextAreaField label="설명" rows={4} error={errors.description?.message} {...register('description')} />
      <SelectField label="카테고리" error={errors.category?.message} {...register('category')}>
        <option value="">선택해 주세요</option>
        {(Object.keys(CATEGORY_LABELS) as KnownCategory[]).map((c) => (
          <option key={c} value={c}>{CATEGORY_LABELS[c]}</option>
        ))}
      </SelectField>
      <TextField label="상품명" error={errors.itemName?.message} {...register('itemName')} />
      <TextField label="구매처 링크" type="url" inputMode="url" error={errors.itemUrl?.message} {...register('itemUrl')} />
      <p>필수예요. https 주소만 쓸 수 있어요.</p>
      <TextField label="개당 가격" type="number" inputMode="numeric" error={errors.unitPrice?.message} {...register('unitPrice')} />
      <p>1~1,000,000원</p>
      <TextField label="총 수량" type="number" inputMode="numeric" error={errors.totalQuantity?.message} {...register('totalQuantity')} />
      <TextField label="내 수량" type="number" inputMode="numeric" error={errors.organizerQuantity?.message} {...register('organizerQuantity')} />
      <p>1 이상, 총 수량보다 적게(2~1,000개 중)</p>
      <p>{`내 부담 금액 ${preview}`}</p>
      <TextField label="마감 (한국 시각)" type="datetime-local" error={errors.deadline?.message} {...register('deadline')} />
      <p>지금부터 30일 이내</p>
      <TextField label="오픈채팅 링크" type="url" inputMode="url" error={errors.openChatUrl?.message} {...register('openChatUrl')} />
      <p>필수예요. https 주소만 쓸 수 있어요. 모집이 끝나면 참여자에게만 보여요.</p>
      {notes}
      {topErrors.map((t) => <p key={t} role="alert">{t}</p>)}
      <Button type="submit" pending={isSubmitting}>{submitLabel}</Button>
    </form>
  )
}
