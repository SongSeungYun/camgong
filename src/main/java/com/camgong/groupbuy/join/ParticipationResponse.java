package com.camgong.groupbuy.join;

import com.camgong.groupbuy.GroupBuyStatus;

/** 본인 정보만 담는다(FR-014): 다른 참여자의 이름·수량은 없다. */
public record ParticipationResponse(Long groupBuyId, int quantity, int amount, int securedQuantity,
        int remainingQuantity, GroupBuyStatus status) {

    static ParticipationResponse of(Long groupBuyId, int quantity, int unitPrice, int securedQuantity, int totalQuantity,
            GroupBuyStatus status) {
        return new ParticipationResponse(groupBuyId, quantity, unitPrice * quantity, securedQuantity,
                totalQuantity - securedQuantity, status);
    }
}
