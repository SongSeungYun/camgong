package com.camgong.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCancelled;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyChanged;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;

/** 커밋 후에 실제로 전달된 이벤트만 모은다(롤백된 트랜잭션의 이벤트는 들어오지 않는다). */
public class EventCollector {

    private final List<GroupBuyChanged> changed = new CopyOnWriteArrayList<>();
    private final List<GroupBuyCancelled> cancelled = new CopyOnWriteArrayList<>();
    private final List<GroupBuyCompleted> completed = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyChanged e) {
        changed.add(e);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyCancelled e) {
        cancelled.add(e);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GroupBuyCompleted e) {
        completed.add(e);
    }

    public List<GroupBuyChanged> changed() {
        return changed;
    }

    public List<GroupBuyCancelled> cancelled() {
        return cancelled;
    }

    public List<GroupBuyCompleted> completed() {
        return completed;
    }

    public void clear() {
        changed.clear();
        cancelled.clear();
        completed.clear();
    }
}
