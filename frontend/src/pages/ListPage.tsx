import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router'
import { uniqueById } from '../shared/lib/uniqueById'
import { useSession } from '../features/auth/session'
import { GroupBuyCard } from '../features/groupbuy/list/GroupBuyCard'
import s from '../features/groupbuy/list/GroupBuyCard.module.css'
import { ListFilters } from '../features/groupbuy/list/ListFilters'
import { SchoolSearch } from '../features/groupbuy/list/SchoolSearch'
import { useListParams } from '../features/groupbuy/list/listParams'
import { useGroupBuyList } from '../features/groupbuy/list/useGroupBuyList'
import { Button, Layout, StateView } from '../shared/ui'

export default function ListPage() {
  const { params: urlParams, qInput, setQInput, setParams } = useListParams()
  const { status, member, retry } = useSession()
  const qc = useQueryClient()
  // 009: 로그인하면 서버가 회원 학교로 거르므로 주소의 school은 보내지 않는다. 확인 중에는 기다린다(전국 목록이 잠깐 섞이지 않게).
  // 로그인 확인이 실패(error)하면 로그인 여부를 모르니 학교 검색을 숨기고 school도 보내지 않는다(서버가 쿠키로 정한다).
  const mySchool = status === 'authenticated' ? (member?.school ?? '') : null
  const anonymous = status === 'anonymous'
  const params = anonymous ? urlParams : { ...urlParams, school: '' }
  const scope = status === 'checking' ? null : status === 'error' ? 'unknown' : (mySchool ?? 'all')
  const q = useGroupBuyList(params, scope)
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  const loaded = uniqueById(q.data?.pages.flatMap((p) => p.items) ?? [])
  // 검수 4: 화면이 아는 범위와 서버가 쓴 범위가 다르면(다른 탭에서 로그인·로그아웃, 세션 만료·탈퇴) 그 목록을 버리고 로그인을 다시 확인한다.
  // 기대 학교: 로그인이면 내 학교, 비로그인이고 학교를 골랐으면 그 학교. 비로그인 전국 목록은 확인할 기준이 없다.
  const expected = mySchool ?? (anonymous && params.school ? params.school : null)
  const mismatch = expected !== null && loaded.some((it) => it.school !== undefined && it.school !== expected)
  const lastFix = useRef(0)
  const [stuck, setStuck] = useState(false)
  const fix = useCallback(() => {
    lastFix.current = Date.now()
    setStuck(false)
    qc.removeQueries({ queryKey: ['groupbuys'] })
    retry()
  }, [qc, retry])
  useEffect(() => {
    if (!mismatch) return
    // 바로 다시 어긋나면(5초 안) 자동으로 되풀이하지 않고 오류와 다시 시도를 보여준다(무한 반복·영원한 로딩 방지)
    if (Date.now() - lastFix.current < 5_000) setStuck(true)
    else fix()
  }, [mismatch, fix])
  const items = mismatch ? [] : loaded
  // FR-014: 정렬을 뺀 조건(검색어·카테고리·기본이 아닌 상태)이 하나라도 걸려 있으면 '결과 없음'
  const filtered = !!params.q || params.category !== null || params.status !== 'OPEN' || !!params.school
  const state = mismatch ? (stuck ? 'error' : 'loading') : q.isPending ? 'loading' : q.isError && items.length === 0 ? 'error' : items.length === 0 ? 'empty' : 'ready'

  return (
    <Layout>
      <h1>공구 목록</h1>
      {notice && <p role="status">{notice}</p>}
      {mySchool !== null && <p>우리 학교 · {mySchool}</p>}
      {anonymous && <SchoolSearch selected={params.school} onSelect={(school) => setParams({ school })} />}
      <ListFilters params={params} qInput={qInput} onQ={setQInput} onChange={setParams} />
      <StateView
        state={state}
        onRetry={mismatch ? fix : () => void q.refetch()}
        emptyText={filtered ? '검색 결과가 없어요. 다른 검색어나 조건을 써보세요.' : '아직 올라온 공구가 없어요.'}
      >
        <ul className={s.list}>
          {items.map((it) => (
            <li key={it.id}>
              <GroupBuyCard item={it} nowMs={q.dataUpdatedAt} showSchool={anonymous} />
            </li>
          ))}
        </ul>
        {q.isFetchNextPageError && <p role="alert">더 불러오지 못했어요. 다시 시도해 주세요.</p>}
        {q.hasNextPage && (
          <Button className={s.more} variant="secondary" pending={q.isFetchingNextPage} onClick={() => void q.fetchNextPage()}>
            더 보기
          </Button>
        )}
      </StateView>
    </Layout>
  )
}
