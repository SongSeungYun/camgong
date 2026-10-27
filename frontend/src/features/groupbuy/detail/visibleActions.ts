import type { GroupBuyDetail } from '../../../shared/api/types'

/**
 * 상세 영역 노출 규칙(data-model.md 3). 버튼 노출은 편의이고 최종 허용은 서버 응답을 따른다.
 * 오픈채팅·명단은 서버 값의 존재 여부만 본다(화면이 조건을 재판단하지 않는다).
 */
export function visibleActions(d: GroupBuyDetail) {
  const open = d.status === 'OPEN'
  const completed = d.status === 'COMPLETED'
  return {
    edit: d.isOrganizer && open,
    join: !d.isOrganizer && d.myParticipation === null && open,
    // 개설자의 수량은 공구 수정에서 바꾼다(ORGANIZER_NOT_ALLOWED)
    changeParticipation: !d.isOrganizer && d.myParticipation !== null && open,
    openChat: d.openChatUrl !== null,
    participants: d.participants !== null,
    checkSheet: d.isOrganizer && completed,
    myConfirm: d.myParticipation !== null && completed,
  }
}
