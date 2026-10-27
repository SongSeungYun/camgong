package com.camgong.auth;

import java.util.concurrent.Executor;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AuthProperties.class)
class AuthConfig {

    @Bean
    PasswordEncoder passwordEncoder(AuthProperties props) {
        return new BCryptPasswordEncoder(props.bcryptCost());
    }

    /**
     * 인증 메일 발송용 실행기. 이름을 붙여 {@code @Async("mailExecutor")} 로 명시한다 — 알림 기능이 전용 Executor 빈을 추가하면서 Boot 의
     * 기본 applicationTaskExecutor 가 사라졌고, 이름 없는 @Async 는 어떤 TaskExecutor 를 고를지 빈 목록에 따라 달라지기 때문이다.
     * 큐가 가득 차면 **거부 예외를 던지지 않고 WARN 만 남기고 버린다**: 예외를 던지면 @Async 프록시가 AFTER_COMMIT 콜백으로 전파해 이미
     * 커밋된 가입·재발송 요청이 500 이 된다. 버려진 메일은 사용자가 재발송으로 복구한다. 스레드는 ThreadPoolExecutor 규칙상 큐가 찬 뒤에야
     * core 를 넘어 늘어나므로 core 와 max 를 같게 둔다. SMTP 가 멈춰도 스레드가 영원히 묶이지 않게 접속·읽기·쓰기 제한 시간은
     * application.yml 의 spring.mail.properties 에 둔다. 테스트는 같은 이름의 SyncTaskExecutor 빈으로 바꿔 요청 스레드에서 바로 보낸다.
     */
    @Bean(name = "mailExecutor")
    @ConditionalOnMissingBean(name = "mailExecutor")
    Executor mailExecutor() {
        return mailPool(2, 200);
    }

    static ThreadPoolTaskExecutor mailPool(int threads, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler((task, pool) ->
                LoggerFactory.getLogger(AuthConfig.class).warn("verification mail dropped: queue full (queue={})", pool.getQueue().size()));
        return executor;
    }
}
