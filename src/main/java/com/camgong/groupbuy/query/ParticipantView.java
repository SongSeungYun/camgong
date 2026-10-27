package com.camgong.groupbuy.query;

/**
 * 개설자용 명단 행.
 * 006 이 같은 명단에 입금·수령 확인 상태를 더하고, 상세의 myParticipation 에도 같은 확인 시각을 채운다.
 * 명단과 내 참여가 같은 쿼리·같은 필드를 쓰므로 둘을 따로 늘리지 말고 함께 바꾼다.
 */
public record ParticipantView(Long memberId, String name, int quantity) {
}
