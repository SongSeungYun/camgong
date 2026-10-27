import { ApiError } from '../api/client'
import { remainingText } from './time'
import { formatWon } from './money'

interface Options {
  /** 폼에서 난 401이면 소실 안내를 덧붙인다 */
  fromForm?: boolean
  nowMs?: number
}

const GENERIC = '잠시 문제가 생겼어요. 다시 시도해 주세요.'

const retryNote = (err: ApiError, nowMs: number) => {
  const at = err.extra.retryAt
  return typeof at === 'string' && remainingText(at, nowMs) ? ` (${remainingText(at, nowMs)})` : ''
}

/** ui-api-map.md 3절 문구표. 서버의 detail은 쓰지 않는다. */
export function errorMessage(err: ApiError, { fromForm = false, nowMs = Date.now() }: Options = {}): string {
  const { status, code, reason, extra } = err
  if (status === 0 || status >= 500) return GENERIC

  switch (code) {
    case 'UNAUTHENTICATED':
      return fromForm ? '로그인이 필요해요. 입력하던 내용은 저장되지 않아요.' : '로그인이 필요해요.'
    case 'INVALID_CREDENTIALS':
      return '이메일 또는 비밀번호가 맞지 않아요.'
    case 'ORGANIZER_NOT_ALLOWED':
      return '개설자는 직접 참여할 수 없어요. 수량은 공구 수정에서 바꿔요.'
    case 'ORGANIZER_ONLY':
      return '개설자만 쓸 수 있어요.'
    case 'PARTICIPATION_NOT_FOUND':
      return '참여 내역이 없어요.'
    case 'NOT_JOINABLE':
      if (reason === 'DEADLINE_PASSED') return '마감 시간이 지나서 참여할 수 없어요.'
      if (reason === 'COMPLETED') return '이미 모집이 끝난 공구예요.'
      if (reason === 'FAILED') return '모집에 실패한 공구예요.'
      return '지금은 참여할 수 없는 공구예요.'
    case 'NOT_MODIFIABLE':
      return '이미 모집이 끝났거나 마감이 지나서 수정·취소할 수 없어요.'
    case 'INSUFFICIENT_QUANTITY':
      return typeof extra.remainingQuantity === 'number'
        ? `남은 수량이 부족해요. 지금 남은 수량은 ${extra.remainingQuantity}개예요.`
        : '남은 수량이 부족해요.'
    case 'PRICE_CHANGED':
      return typeof extra.currentUnitPrice === 'number'
        ? `개당 가격이 바뀌었어요(${formatWon(extra.currentUnitPrice)}). 금액을 다시 확인해 주세요.`
        : '개당 가격이 바뀌었어요. 금액을 다시 확인해 주세요.'
    case 'ALREADY_JOINED':
      return "이미 참여했어요. 수량은 '수량 변경'을 써요."
    case 'BUSY':
      return '요청이 몰렸어요. 잠시 뒤 다시 시도해 주세요.'
    case 'DUPLICATE_REQUEST':
      return '같은 공구가 이미 열려 있어요.'
    case 'NOT_CHECKABLE':
      return '모집이 완료된 공구에서만 체크할 수 있어요.'
    case 'ALREADY_REGISTERED':
      return '이미 가입된 이메일이에요. 로그인해 주세요.'
    case 'DISPLAY_NAME_TAKEN':
      return '이미 쓰이는 이름이에요. 다른 이름을 입력해 주세요.'
    case 'TOKEN_USED':
      return '이미 쓴 링크예요.'
    case 'TOKEN_EXPIRED':
      return '링크가 만료됐어요.'
    case 'INVALID_TOKEN':
      return '올바르지 않은 링크예요.'
    case 'TOO_MANY_REQUESTS':
      return `잠시 뒤에 다시 시도해 주세요.${retryNote(err, nowMs)}`
    case 'LOGIN_LOCKED':
      return `로그인 시도가 너무 많아요. 잠시 뒤 다시 시도해 주세요.${retryNote(err, nowMs)}`
  }

  // 코드 없는 상태 기준 문구
  if (status === 400) return '입력한 내용을 확인해 주세요.'
  if (status === 401) return fromForm ? '로그인이 필요해요. 입력하던 내용은 저장되지 않아요.' : '로그인이 필요해요.'
  if (status === 404) return '찾을 수 없는 공구예요.'
  return GENERIC // 알 수 없는 코드·상태
}
