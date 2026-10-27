import { Link } from 'react-router'
import { categoryLabel, type GroupBuyDetail as Detail } from '../../../shared/api/types'
import { formatWon } from '../../../shared/lib/money'
import { externalLinkProps, safeHttpsUrl } from '../../../shared/lib/safeLink'
import { formatKst, remainingText } from '../../../shared/lib/time'
import { Badge, Button, ProgressBar } from '../../../shared/ui'
import s from './GroupBuyDetail.module.css'
import { OrganizerActions } from './OrganizerActions'
import { visibleActions } from './visibleActions'

/** 서버 텍스트는 JSX 텍스트로만 렌더링한다(FR-010). nowMs는 불러온 시점. */
export function GroupBuyDetail({ d, nowMs }: { d: Detail; nowMs: number }) {
  const v = visibleActions(d)
  const itemUrl = safeHttpsUrl(d.itemUrl)
  const chatUrl = safeHttpsUrl(d.openChatUrl)
  const my = d.myParticipation
  return (
    <article>
      <h1>{d.title}</h1>
      <p><Badge status={d.status} /> {categoryLabel(d.category)}</p>
      {d.description && <p className={s.desc}>{d.description}</p>}
      <div className={s.facts}>
        <p>{d.itemName}</p>
        {itemUrl ? (
          <p><a href={itemUrl} {...externalLinkProps}>구매처 보기 (새 창, 서비스 밖)</a></p>
        ) : d.itemUrl ? (
          <p>구매처: {d.itemUrl}</p> // https가 아니면 링크로 만들지 않고 글자로만 보여준다(FR-009)
        ) : null}
        <p>개당 {formatWon(d.unitPrice)}</p>
        <ProgressBar value={d.securedQuantity} max={d.totalQuantity} />
        <p>총 {d.totalQuantity}개 · 확보 {d.securedQuantity}개 · 남은 {d.remainingQuantity}개</p>
        <p>마감 {formatKst(d.deadline)} · {remainingText(d.deadline, nowMs)}</p>
        <p>개설자 <span>{d.organizerName}</span></p>
        <p>참여 인원 {d.participantCount}명</p>
        <p>{my ? `내 참여: ${my.quantity}개 · ${formatWon(my.amount)}` : '참여 안 함'}</p>
        {v.myConfirm && my && (
          <p>
            {my.paidConfirmedAt ? `입금 확인됨 (${formatKst(my.paidConfirmedAt)})` : '입금 확인 전'} ·{' '}
            {my.receivedConfirmedAt ? `수령 확인됨 (${formatKst(my.receivedConfirmedAt)})` : '수령 확인 전'}
          </p>
        )}
      </div>

      <div className={s.actions}>
        {v.edit && <OrganizerActions d={d} />}
        {/* ponytail: 아래 변경 버튼은 US4에서 동작을 붙인다. 그 전까지 비활성. */}
        {v.join && <Button disabled>참여하기</Button>}
        {v.changeParticipation && <Button variant="secondary" disabled>수량 변경</Button>}
        {v.changeParticipation && <Button variant="secondary" disabled>참여 취소</Button>}
        {v.checkSheet && <Link className={s.linkButton} to={`/groupbuys/${d.id}/check`}>체크표</Link>}
        {v.openChat && chatUrl && <a className={s.linkButton} href={chatUrl} {...externalLinkProps}>오픈채팅 입장 (새 창, 서비스 밖)</a>}
      </div>

      {v.participants && d.participants && (
        <ul className={s.people} aria-label="참여자">
          {d.participants.map((p) => (
            <li key={p.memberId}><span>{p.name}</span> {p.quantity}개</li>
          ))}
        </ul>
      )}
    </article>
  )
}
