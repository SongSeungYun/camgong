package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.camgong.support.IntegrationTestBase;

/**
 * 인증 메일 리스너는 이름 붙은 실행기를 명시해서 쓴다. 알림 기능이 전용 Executor 빈을 더하면서 Boot 의 기본 applicationTaskExecutor 가
 * 사라졌기 때문에, 이름 없는 @Async 는 빈 목록에 따라 엉뚱한 실행기(알림 풀)를 고를 수 있다.
 */
class MailExecutorWiringTest extends IntegrationTestBase {

    @Autowired
    ApplicationContext context;

    @Test
    void theVerificationListenerNamesItsExecutor() throws Exception {
        Method on = VerificationMailListener.class.getDeclaredMethod("on", VerificationMailRequested.class);
        assertThat(on.getAnnotation(Async.class).value()).isEqualTo("mailExecutor");
    }

    @Test
    void testsRunMailSendingSynchronouslyOnTheNamedExecutorAndTheNotificationPoolIsASeparateBean() {
        assertThat(context.getBean("mailExecutor")).isInstanceOf(SyncTaskExecutor.class);
        assertThat(context.getBean("notificationExecutor")).isNotSameAs(context.getBean("mailExecutor"));
    }

    @Test
    void theProductionMailExecutorIsABoundedPoolThatRejectsWhenFull() {
        var executor = (ThreadPoolTaskExecutor) new AuthConfig().mailExecutor();
        executor.initialize();
        try {
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }
}
