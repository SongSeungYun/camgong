import type { FieldPath, FieldValues, UseFormSetError } from 'react-hook-form'
import type { ApiError } from '../api/client'
import { errorMessage } from './errorMessage'

/**
 * 서버 errors[]를 폼 필드 옆에 붙인다. 폼에 없는 필드의 오류와 코드 있는 오류는
 * 폼 상단에 보일 문구 목록으로 돌려준다(입력값은 유지된다).
 * 같은 필드에 오류가 둘 이상이면 " / "로 모아 모두 보여준다(001 계약, ui-api-map 3절).
 * 처리 중 비활성화된 제출 버튼에서 포커스가 사라지므로, fields 순서(화면 순서)상 첫 오류 필드로 포커스를 옮긴다.
 */
export function applyServerErrors<T extends FieldValues>(err: ApiError, setError: UseFormSetError<T>, fields: readonly string[]): string[] {
  if (err.fieldErrors.length === 0) return [errorMessage(err)]
  const rest: string[] = []
  const byField = new Map<string, string[]>()
  for (const fe of err.fieldErrors) {
    if (fields.includes(fe.field)) byField.set(fe.field, [...(byField.get(fe.field) ?? []), fe.message])
    else rest.push(fe.message)
  }
  const first = fields.find((f) => byField.has(f))
  for (const [field, messages] of byField) setError(field as FieldPath<T>, { type: 'server', message: messages.join(' / ') }, { shouldFocus: field === first })
  return rest
}
