import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { Button } from '../../../shared/ui'
import { fetchSchools } from '../api'
import s from './GroupBuyCard.module.css'

const Q_MAX = 50
const DEBOUNCE_MS = 300
const normalize = (raw: string) => [...raw.trim()].slice(0, Q_MAX).join('').toLowerCase()

/**
 * 009 비로그인 학교 검색(콤보박스, APG 방식). 후보 중 하나를 골라야 목록이 걸러진다(정확한 학교 키로만).
 * 키보드: ↓↑로 후보 이동, Enter로 고름, Esc로 닫음. 고르거나 닫아도 포커스는 입력칸에 남는다.
 */
export function SchoolSearch({ selected, onSelect }: { selected: string; onSelect: (school: string) => void }) {
  const id = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [text, setText] = useState('')
  const [term, setTerm] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [announce, setAnnounce] = useState('')

  useEffect(() => {
    const t = setTimeout(() => setTerm(normalize(text)), DEBOUNCE_MS)
    return () => clearTimeout(t)
  }, [text])

  const q = useQuery({ queryKey: ['schools', term], queryFn: () => fetchSchools(term), enabled: term !== '', staleTime: 60_000, retry: false, placeholderData: keepPreviousData })
  const typed = normalize(text)
  const searching = open && typed !== ''
  // 이전 검색어의 응답(디바운스·placeholder)이 남아 있어도 지금 입력에 맞는 후보만 보이고 골라진다
  const items = searching ? (q.data?.items ?? []).filter((it) => it.includes(typed)) : []
  const fresh = term === typed && !q.isPlaceholderData // 응답이 지금 입력에 대한 것인지
  const showList = items.length > 0
  const itemsKey = items.join('\n')
  useEffect(() => setActive(-1), [itemsKey]) // 후보가 바뀌면 하이라이트를 버린다(없는 후보를 가리키지 않게)

  const optionId = (i: number) => `${id}-opt-${i}`
  const status = searching && fresh && q.isError
    ? '학교 후보를 불러오지 못했어요. 다시 입력해 주세요.'
    : searching && fresh && q.isSuccess && items.length === 0
      ? '맞는 학교가 없어요.'
      : showList
        ? `학교 후보 ${items.length}개`
        : announce

  const close = () => {
    setOpen(false)
    setActive(-1)
  }
  const choose = (school: string) => {
    onSelect(school)
    setText('')
    setTerm('')
    setAnnounce(`${school} 공구만 보여요.`)
    close()
  }
  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      setOpen(true)
      if (items.length === 0) return
      const n = items.length
      setActive((i) => (e.key === 'ArrowDown' ? (i + 1) % n : i <= 0 ? n - 1 : i - 1))
    } else if (e.key === 'Enter') {
      const pick = showList ? items[active] : undefined
      if (!pick) return
      e.preventDefault()
      choose(pick)
    } else if (e.key === 'Escape' && open) {
      e.preventDefault()
      close()
    }
  }

  return (
    <section aria-label="학교" className={s.school}>
      <label htmlFor={`${id}-input`}>학교 검색</label>
      <input
        ref={inputRef}
        id={`${id}-input`}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={showList}
        aria-controls={`${id}-list`}
        aria-activedescendant={showList && active >= 0 ? optionId(active) : undefined}
        aria-describedby={`${id}-hint`}
        autoComplete="off"
        placeholder="예: snu"
        value={text}
        onChange={(e) => {
          setText(e.target.value)
          setAnnounce('')
          setOpen(true)
          setActive(-1)
        }}
        onFocus={() => setOpen(true)} // 모바일에서 키보드를 내렸다 다시 눌러도 후보가 열린다
        onKeyDown={onKeyDown}
        onBlur={close}
      />
      <p id={`${id}-hint`} className={s.meta}>학교 메일 주소의 도메인으로 찾아요(예: snu.ac.kr). 후보에서 골라야 목록이 바뀌어요.</p>
      {/* 접혀도 늘 렌더해 aria-controls가 가리키는 대상을 유지한다 */}
      <ul id={`${id}-list`} role="listbox" aria-label="학교 후보" className={s.options} hidden={!showList}>
        {items.map((it, i) => (
          <li
            key={it}
            id={optionId(i)}
            role="option"
            aria-selected={i === active}
            className={s.option}
            onMouseDown={(e) => e.preventDefault()} // 입력칸 포커스(blur로 닫힘)를 지킨다
            onClick={() => choose(it)}
          >
            {it}
          </li>
        ))}
      </ul>
      {/* 늘 붙어 있는 상태 영역(검색 피드백과 고르거나 풀었을 때의 알림). 내용만 바꿔야 화면 읽기 프로그램이 놓치지 않는다 */}
      <p role="status" data-testid="school-status" className={s.meta}>{status}</p>
      {selected && (
        <p>
          <span>선택한 학교 · {selected}</span>{' '}
          <Button
            variant="secondary"
            onClick={() => {
              onSelect('')
              setAnnounce('전국 공구를 보여요.')
              inputRef.current?.focus() // 사라지는 버튼 대신 입력칸으로
            }}
          >
            학교 선택 해제
          </Button>
        </p>
      )}
    </section>
  )
}
