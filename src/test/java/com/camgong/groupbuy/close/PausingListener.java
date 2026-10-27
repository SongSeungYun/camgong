package com.camgong.groupbuy.close;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCancelled;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyChanged;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyCompleted;
import com.camgong.groupbuy.GroupBuyEvents.GroupBuyFailed;

/**
 * 트랜잭션을 커밋 직전에 멈춰 세워 "누가 공구 행 락을 쥔 채 다른 쪽이 도착하는가"를 결정적으로 만든다.
 * arm(이벤트 타입)하면 그 타입의 이벤트를 발행한 트랜잭션이 BEFORE_COMMIT 에서 proceed 신호를 기다린다(그동안 공구 행 락을 쥐고 있다).
 */
final class PausingListener {

    private static final AtomicReference<Pause> ACTIVE = new AtomicReference<>();

    private PausingListener() {
    }

    static final class Pause {
        final Class<?> eventType;
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);

        Pause(Class<?> eventType) {
            this.eventType = eventType;
        }

        /** 멈춘 트랜잭션이 락을 쥔 채 대기 중이 될 때까지 기다린다. */
        void awaitReached() throws InterruptedException {
            if (!reached.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("트랜잭션이 " + eventType.getSimpleName() + " 에서 멈추지 않았다");
            }
        }

        void release() {
            proceed.countDown();
        }
    }

    static Pause arm(Class<?> eventType) {
        Pause pause = new Pause(eventType);
        ACTIVE.set(pause);
        return pause;
    }

    static void disarm() {
        ACTIVE.set(null);
    }

    private static void pauseIf(Class<?> type) {
        Pause pause = ACTIVE.get();
        if (pause != null && pause.eventType == type && pause.reached.getCount() > 0) {
            pause.reached.countDown();
            try {
                pause.proceed.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static class Listener {
        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        void on(GroupBuyFailed e) {
            pauseIf(GroupBuyFailed.class);
        }

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        void on(GroupBuyCompleted e) {
            pauseIf(GroupBuyCompleted.class);
        }

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        void on(GroupBuyChanged e) {
            pauseIf(GroupBuyChanged.class);
        }

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        void on(GroupBuyCancelled e) {
            pauseIf(GroupBuyCancelled.class);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        Listener pausingListener() {
            return new Listener();
        }
    }
}
