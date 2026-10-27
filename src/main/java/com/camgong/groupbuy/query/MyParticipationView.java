package com.camgong.groupbuy.query;

import java.time.OffsetDateTime;

/**
 * 상세의 내 참여. 개설자 본인의 것에는 입금·수령 확인 필드가 아예 없고(contracts), 일반 참여자는 두 필드가 있되
 * 개설자가 확인하기 전에는 null 이다. 두 확인 시각은 006 에서 채운다.
 */
public sealed interface MyParticipationView {

    int quantity();

    int amount();

    record Organizer(int quantity, int amount) implements MyParticipationView {
    }

    record Participant(int quantity, int amount, OffsetDateTime paidConfirmedAt, OffsetDateTime receivedConfirmedAt)
            implements MyParticipationView {
    }
}
