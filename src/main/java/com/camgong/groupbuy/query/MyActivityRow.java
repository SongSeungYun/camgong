package com.camgong.groupbuy.query;

import java.time.Instant;

import com.camgong.groupbuy.GroupBuyCategory;
import com.camgong.groupbuy.GroupBuyStatus;

/** 내 활동 쿼리의 프로젝션 행. 표시 상태는 서비스가 마감과 시계로 계산한다. myQuantity 는 participant 탭에서만 있다. */
public record MyActivityRow(Long id, String title, String itemName, GroupBuyCategory category, int unitPrice,
        int securedQuantity, int totalQuantity, Instant deadline, GroupBuyStatus status, Integer myQuantity) {

    /** organizer 탭용: 내 수량 없음. */
    public MyActivityRow(Long id, String title, String itemName, GroupBuyCategory category, int unitPrice,
            int securedQuantity, int totalQuantity, Instant deadline, GroupBuyStatus status) {
        this(id, title, itemName, category, unitPrice, securedQuantity, totalQuantity, deadline, status, null);
    }
}
