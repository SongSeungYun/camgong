import type { FieldPath, FieldValues, UseFormSetError } from 'react-hook-form'
import type { ApiError } from '../api/client'
import { errorMessage } from './errorMessage'

/**
 * 서버 errors[]를 폼 필드 옆에 붙인다. 폼에 없는 필드의 오류와 코드 있는 오류는
 * 폼 상단에 보일 문구 목록으로 돌려준다(입력값은 유지된다).
 * 처리 중 비활성화된 제출 버튼에서 포커스가 사라지므로, fields 순서(화면 순서)상 첫 오류 필드로 포커스를 옮긴다.
 */
export function applyServerErrors<T extends FieldValues>(err: ApiError, setError: UseFormSetError<T>, fields: readonly string[]): string[] {
  if (err.fieldErrors.length === 0) return [errorMessage(err)]
  const rest: string[] = []
  const first = fields.find((f) => err.fieldErrors.some((fe) => fe.field === f))
  for (const fe of err.fieldErrors) {
    if (fields.includes(fe.field)) setError(fe.field as FieldPath<T>, { type: 'server', message: fe.message }, { shouldFocus: fe.field === first })
    else rest.push(fe.message)
  }
  return rest
}
