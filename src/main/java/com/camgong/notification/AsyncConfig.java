package com.camgong.notification;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 알림 전용 실행기. 큐가 가득 차면 **거부**한다(AbortPolicy) — CallerRunsPolicy 는 원 요청 스레드가 알림을 만들게 해 요청을 느리게 하므로
 * 쓰지 않는다(FR-012, research 결정 10). 거부된 알림은 리스너가 WARN 으로 남기고 유실로 취급한다(허용된 한계).
 */
@Configuration
class AsyncConfig {

    // ponytail: 가득 차면 유실. 신뢰성이 필요하면 아웃박스(research 결정 1의 C)
    @Bean(name = "notificationExecutor")
    Executor notificationExecutor(NotificationProperties props) {
        if (!props.async().enabled()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notification-");
        executor.setCorePoolSize(props.async().coreSize());
        executor.setMaxPoolSize(props.async().maxSize());
        executor.setQueueCapacity(props.async().queueCapacity());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
