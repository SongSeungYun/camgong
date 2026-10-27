export interface FieldError {
  field: string
  message: string
}

/** 서버 ProblemDetail을 화면용으로 바꾼 오류. 서버의 detail/스택은 담지 않는다. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code?: string,
    readonly reason?: string,
    readonly fieldErrors: FieldError[] = [],
    /** currentUnitPrice, remainingQuantity, retryAt 등 코드별 추가 속성 */
    readonly extra: Record<string, unknown> = {},
  ) {
    super(code ?? `HTTP ${status}`)
  }
}

const KNOWN_KEYS = new Set(['type', 'title', 'status', 'detail', 'instance', 'errors', 'reason'])

let onUnauthenticated: () => void = () => {}
export const setUnauthenticatedHandler = (fn: () => void) => {
  onUnauthenticated = fn
}

async function toApiError(res: Response): Promise<ApiError> {
  // 5xx는 본문을 보지 않고 일반 오류로 만든다(서버 정보 비노출).
  if (res.status >= 500) return new ApiError(res.status)
  let body: Record<string, unknown> = {}
  try {
    body = await res.json()
  } catch {
    // 본문 없음/JSON 아님
  }
  const extra = Object.fromEntries(Object.entries(body).filter(([k]) => !KNOWN_KEYS.has(k)))
  const errors = Array.isArray(body.errors) ? (body.errors as FieldError[]) : []
  return new ApiError(
    res.status,
    typeof body.title === 'string' ? body.title : undefined,
    typeof body.reason === 'string' ? body.reason : undefined,
    errors,
    extra,
  )
}

interface Options {
  /** 시작 시 me 조회처럼 401이 '정상 응답'인 호출은 세션 만료 처리(캐시 비우기)를 하지 않는다 */
  expect401?: boolean
}

export async function api<T = void>(method: string, path: string, body?: unknown, { expect401 = false }: Options = {}): Promise<T> {
  let res: Response
  try {
    res = await fetch(new URL(path, location.href), {
      method,
      credentials: 'include',
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0) // 네트워크 오류
  }
  if (!res.ok) {
    const err = await toApiError(res)
    // 로그인 실패(INVALID_CREDENTIALS)는 세션 만료가 아니므로 제외한다.
    if (err.status === 401 && err.code !== 'INVALID_CREDENTIALS' && !expect401) onUnauthenticated()
    throw err
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const get = <T>(path: string, opts?: Options) => api<T>('GET', path, undefined, opts)
export const post = <T = void>(path: string, body?: unknown) => api<T>('POST', path, body)
export const put = <T = void>(path: string, body?: unknown) => api<T>('PUT', path, body)
export const del = <T = void>(path: string) => api<T>('DELETE', path)
