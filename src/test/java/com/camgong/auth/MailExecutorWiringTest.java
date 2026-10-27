package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.camgong.support.IntegrationTestBase;

/**
 * 인증 메일 리스너는 이름 붙은 실행기를 명시해서 쓴다. 알림 기능이 전용 Executor 빈을 더하면서 Boot 의 기본 applicationTaskExecutor 가
 * 사라졌기 때문에, 이름 없는 @Async 는 빈 목록에 따라 엉뚱한 실행기(알림 풀)를 고를 수 있다.
 */
@ExtendWith(OutputCaptureExtension.class)
class MailExecutorWiringTest extends IntegrationTestBase {

    @Autowired
    ApplicationContext context;

    @Test
    void testsRunMailSendingSynchronouslyOnTheNamedExecutorAndTheNotificationPoolIsASeparateBean() {
        assertThat(context.getBean("mailExecutor")).isInstanceOf(SyncTaskExecutor.class);
        assertThat(context.getBean("notificationExecutor")).isNotSameAs(context.getBean("mailExecutor"));
    }

    @Test
    void theListenerSwallowsAQueueRejectionAndLogsOnlyTheMaskedRecipient(CapturedOutput output) {
        var executor = AuthConfig.mailPool(1, 1);                                       // 스레드 1 + 큐 1
        executor.initialize();
        var release = new java.util.concurrent.CountDownLatch(1);
        var sender = new MailSender() {
            @Override
            public void sendVerification(String to, String link) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        var props = org.mockito.Mockito.mock(AuthProperties.class);
        org.mockito.Mockito.when(props.verifyLinkBaseUrl()).thenReturn("http://x/verify?token=");
        var listener = new VerificationMailListener(sender, props, executor);
        try {
            for (int i = 0; i < 5; i++) {
                listener.on(new VerificationMailRequested("student" + i + "@school.ac.kr", "tok" + i));    // 가득 찬 뒤에도 예외가 나오지 않는다
            }
            assertThat(output.getAll()).contains("verification mail dropped to=s***@school.ac.kr").doesNotContain("student1@school.ac.kr");
        } finally {
            release.countDown();
            executor.getThreadPoolExecutor().shutdown();
        }
    }

    @Test
    void theProductionPoolHasNoIdleCoreGapBelowItsMax() {
        var executor = (ThreadPoolTaskExecutor) new AuthConfig().mailExecutor();
        assertThat(executor.getCorePoolSize()).isEqualTo(executor.getMaxPoolSize());     // 큐가 차야 늘어나는 풀이면 실제 스레드는 core 뿐이다
    }
}
