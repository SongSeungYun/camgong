package com.camgong.auth;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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
     * 메일이 몰리면 요청이 아니라 큐가 받는다(가득 차면 호출자 스레드가 보내는 대신 거부되어 로그만 남는다 — 사용자는 재발송으로 복구).
     * 테스트는 같은 이름의 SyncTaskExecutor 빈으로 바꿔 요청 스레드에서 바로 보낸다.
     */
    @Bean(name = "mailExecutor")
    @ConditionalOnMissingBean(name = "mailExecutor")
    Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
