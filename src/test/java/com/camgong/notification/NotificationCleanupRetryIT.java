package com.camgong.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 010 T024: 정리 두 개가 동시에 돌면 DELETE … LIMIT 끼리 드물게 교착(1213)한다(NotificationCleanupIT 동시 실행에서 60회 중 1회 재현).
 * 교착으로 진 묶음은 다시 지운다.
 */
class NotificationCleanupRetryIT extends NotificationTestBase {

    @MockitoSpyBean
    NotificationCleanup cleanup;

    @Test
    void aDeadlockedBatchIsRetriedAndEverythingIsStillDeleted() {
        insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START, false);
        insertNotification(OUTSIDER, "GROUPBUY_FAILED", START, false);
        clock.set(START.plus(Duration.ofDays(31)));
        doThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"))
                .doCallRealMethod().when(cleanup).deleteBatch(any());

        assertThat(cleanup.run()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification", Integer.class)).isZero();
    }

    @Test
    void aBatchThatKeepsDeadlockingFailsTheRun() {
        insertNotification(PARTICIPANT, "GROUPBUY_COMPLETED", START, false);
        clock.set(START.plus(Duration.ofDays(31)));
        doThrow(new CannotAcquireLockException("Deadlock found when trying to get lock")).when(cleanup).deleteBatch(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(cleanup::run).isInstanceOf(CannotAcquireLockException.class);
    }
}
