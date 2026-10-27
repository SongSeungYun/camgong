package com.camgong.groupbuy.close;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/** quickstart 시나리오 16: 스케줄러가 켜져 있으면 스스로 확정하고, 꺼져 있으면 등록되지 않는다. */
class CloseSchedulerWiringTest {

    @Nested
    @TestPropertySource(properties = { "camgong.scheduler.close.enabled=true", "camgong.scheduler.close.interval=200ms" })
    class Enabled extends CloseTestBase {

        @Autowired
        ApplicationContext context;

        @Test
        void anExpiredGroupBuyIsClosedWithinOnePeriodWithoutAnyoneCallingTheService() throws Exception {
            long id = openUnderfilled("스스로 확정");
            clock.set(T.plusSeconds(5));

            long deadline = System.currentTimeMillis() + 10_000;
            while (!"FAILED".equals(statusOf(id)) && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }

            assertThat(statusOf(id)).isEqualTo("FAILED");
            assertThat(context.getBeanNamesForType(CloseExpiredScheduler.class)).hasSize(1);
        }
    }

    @Nested
    class Disabled extends CloseTestBase {

        @Autowired
        ApplicationContext context;

        @Test
        void noSchedulerBeanIsRegisteredAndNothingIsClosedOnItsOwn() throws Exception {
            long id = openUnderfilled("스케줄러 꺼짐");
            clock.set(T.plusSeconds(5));
            Thread.sleep(500);

            assertThat(context.getBeanNamesForType(CloseExpiredScheduler.class)).isEmpty();
            assertThat(statusOf(id)).isEqualTo("OPEN");
        }
    }
}
