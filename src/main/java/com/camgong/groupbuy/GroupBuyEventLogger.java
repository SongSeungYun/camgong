package com.camgong.groupbuy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCancelled;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyChanged;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;

// ponytail: 커밋 후 유실 가능, 알림 기능에서 전달 수단 연결·신뢰성 필요하면 아웃박스로
@Component
class GroupBuyEventLogger {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyEventLogger.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyChanged e) {
        log.info("GroupBuyChanged groupBuyId={} recipients={} fields={}", e.groupBuyId(), e.recipientIds(),
                e.changedFields());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyCancelled e) {
        log.info("GroupBuyCancelled groupBuyId={} recipients={}", e.groupBuyId(), e.recipientIds());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyCompleted e) {
        log.info("GroupBuyCompleted groupBuyId={} recipients={}", e.groupBuyId(), e.recipientIds());
    }
}
