import { CATEGORY_LABELS, STATUSES, type KnownCategory } from '../../../shared/api/types'
import { SelectField, TextField } from '../../../shared/ui'
import s from './GroupBuyCard.module.css'
import type { ListParams } from './listParams'

const STATUS_OPTIONS: Record<(typeof STATUSES)[number], string> = {
  OPEN: '모집 중',
  PENDING_CLOSE: '마감됨(확정 대기)',
  COMPLETED: '모집 완료',
  FAILED: '모집 실패',
}

interface Props {
  params: ListParams
  qInput: string
  onQ: (q: string) => void
  onChange: (patch: Partial<ListParams>) => void
}

export function ListFilters({ params, qInput, onQ, onChange }: Props) {
  return (
    <section aria-label="조건">
      <TextField label="검색" type="search" value={qInput} maxLength={50} placeholder="제목·상품명" onChange={(e) => onQ(e.target.value)} />
      <div className={s.row}>
        <SelectField label="정렬" value={params.sort} onChange={(e) => onChange({ sort: e.target.value as ListParams['sort'] })}>
          <option value="latest">최신순</option>
          <option value="deadline">마감 임박순</option>
        </SelectField>
        <SelectField label="상태" value={params.status} onChange={(e) => onChange({ status: e.target.value })}>
          {STATUSES.map((st) => (
            <option key={st} value={st}>{STATUS_OPTIONS[st]}</option>
          ))}
        </SelectField>
      </div>
      <div className={s.chips} role="group" aria-label="카테고리">
        <button type="button" className={s.chip} aria-pressed={params.category === null} onClick={() => onChange({ category: null })}>전체</button>
        {(Object.keys(CATEGORY_LABELS) as KnownCategory[]).map((c) => (
          <button key={c} type="button" className={s.chip} aria-pressed={params.category === c} onClick={() => onChange({ category: c })}>
            {CATEGORY_LABELS[c]}
          </button>
        ))}
      </div>
    </section>
  )
}
