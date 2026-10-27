import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { isKnownCategory, isKnownStatus } from '../../../shared/api/types'

export interface ListParams {
  status: string
  category: string | null
  q: string
  sort: 'latest' | 'deadline'
}

const DEFAULTS: ListParams = { status: 'OPEN', category: null, q: '', sort: 'latest' }
const Q_MAX = 50
const DEBOUNCE_MS = 300

/** 앞뒤 공백 제거, 공백뿐이면 검색 안 함(''), 50자 제한. */
export const normalizeQ = (raw: string): string => raw.trim().slice(0, Q_MAX)

/** 주소 쿼리 → 조건. 알 수 없는 값은 기본값으로 되돌린다. 커서는 읽지 않는다. */
export function parseListParams(sp: URLSearchParams): ListParams {
  const status = sp.get('status') ?? ''
  const category = sp.get('category') ?? ''
  const sort = sp.get('sort')
  return {
    status: isKnownStatus(status) ? status : DEFAULTS.status,
    category: isKnownCategory(category) ? category : null,
    q: normalizeQ(sp.get('q') ?? ''),
    sort: sort === 'deadline' ? 'deadline' : 'latest',
  }
}

/** 조건 → 주소 쿼리. 기본값은 생략한다. */
export function serializeListParams(p: ListParams): URLSearchParams {
  const sp = new URLSearchParams()
  if (p.status !== DEFAULTS.status) sp.set('status', p.status)
  if (p.category) sp.set('category', p.category)
  if (p.q) sp.set('q', p.q)
  if (p.sort !== DEFAULTS.sort) sp.set('sort', p.sort)
  return sp
}

export function useListParams() {
  const [sp, setSp] = useSearchParams()
  const params = useMemo(() => parseListParams(sp), [sp])
  const [qInput, setQInput] = useState(params.q)

  const setParams = (patch: Partial<ListParams>) =>
    setSp(serializeListParams({ ...params, ...patch }), { replace: true })

  // 검색어는 디바운스 후 주소에 반영한다(키 입력마다 요청하지 않음).
  // ponytail: 주소가 밖에서 바뀌면(뒤로 가기 등) 입력칸은 따라가지 않는다. 필요해지면 params.q로 동기화.
  useEffect(() => {
    const next = normalizeQ(qInput)
    if (next === params.q) return
    const t = setTimeout(() => setParams({ q: next }), DEBOUNCE_MS)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- setParams는 params에서 파생
  }, [qInput, params.q])

  return { params, qInput, setQInput, setParams }
}
