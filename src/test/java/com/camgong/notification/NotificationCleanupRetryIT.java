package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 010 T024: 정리 두 개가 동시에 돌면 DELETE … LIMIT 끼리 드물게 교착(1213)한다(NotificationCleanupIT 동시 실행에서 60회 중 1회 재현).
 * 교착으로 진 묶음은 다시 지운다. 스프링 빈을 바꾸지 않고(@MockitoSpyBean 은 컨텍스트를 하나 더 만든다) 직접 만든 정리 객체를 감싼다.
 */
class NotificationCleanupRetryIT extends NotificationTestBase {

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    NotificationProperties props;

    NotificationCleanup cleanup;

    @BeforeEach
    void cleanup() {
        cleanup = spy(new NotificationCleanup(jdbcClient, clock, props));
    }

    @Test
    void aDeadlockedBatchIsRetriedAndEverythingIsStillDeleted() {
        insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START, false);
        insertNotification(OUTSIDER, "GROUPBUY_FAILED", START, false);
        clock.set(START.plus(Duration.ofDays(31)));
        doThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"))
                .doCallRealMethod().when(cleanup).deleteBatch(any());

        assertThat(cleanup.run()).isEqualTo(2);
        assertThat(notificationCount()).isZero();
    }

    @Test
    void aBatchThatKeepsDeadlockingFailsTheRun() {
        insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START, false);
        clock.set(START.plus(Duration.ofDays(31)));
        doThrow(new CannotAcquireLockException("Deadlock found when trying to get lock")).when(cleanup).deleteBatch(any());

        assertThatThrownBy(cleanup::run).isInstanceOf(CannotAcquireLockException.class);
    }
}
