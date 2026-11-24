package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 011 T004: 재설정 메일은 요청 스레드 밖에서 보낸다. 회원 주소만 SMTP 시간만큼 늦으면 응답 시간으로 가입이 드러난다(plan 결정 2).
 * 운영 mailExecutor 가 스레드 풀인 것은 MailExecutorWiringTest 가 본다(통합 테스트는 같은 이름의 동기 실행기로 바꾼다).
 */
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetMailTest {

    @Test
    void theListenerReturnsWhileTheMailIsStillSendingAndAFailureOnlyWarns(CapturedOutput output) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MailSender stuck = new MailSender() {
            @Override
            public void sendVerification(String toEmail, String verifyLink) {
            }

            @Override
            public void sendPasswordReset(String toEmail, String resetLink) {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new MailSendException("smtp down");
            }
        };
        ThreadPoolTaskExecutor pool = AuthConfig.mailPool(1, 10);
        pool.initialize();
        AuthProperties props = new AuthProperties(null, null, null, null, null, null, true, 4, "http://v/?token=", null,
                "http://r/?token=");
        var listener = new VerificationMailListener(stuck, props, pool);

        try {
            // 요청 스레드에서 보내면 발송이 풀릴 때까지 여기서 막힌다
            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> listener.on(new PasswordResetMailRequested("member@school.ac.kr", "secret-token-123")));
            assertThat(entered.await(5, TimeUnit.SECONDS)).as("다른 스레드에서 보내기 시작했다").isTrue();
        } finally {
            release.countDown();
        }
        pool.shutdown();
        assertThat(pool.getThreadPoolExecutor().awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(output).contains("password reset mail failed to=m***@school.ac.kr cause=MailSendException")
                .doesNotContain("member@school.ac.kr").doesNotContain("secret-token-123");
    }
}
