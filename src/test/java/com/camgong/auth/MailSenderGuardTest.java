package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** logging 발송기는 개발용이다: 운영 프로파일에서는 기동을 막고, 쌓는 기록도 최근 몇 건으로 제한한다. */
class MailSenderGuardTest {

    private static MockEnvironment env(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }

    @Test
    void loggingSenderRefusesToStartOutsideLocalTestAndBenchmark() {
        assertThatThrownBy(() -> new LoggingMailSender(env())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LoggingMailSender(env("prod"))).isInstanceOf(IllegalStateException.class);
        for (String profile : new String[] { "local", "test", "benchmark" }) {
            assertThatCode(() -> new LoggingMailSender(env(profile))).doesNotThrowAnyException();
        }
    }

    @Test
    void sentListKeepsOnlyTheMostRecentHundred() {
        LoggingMailSender sender = new LoggingMailSender(env("test"));
        for (int i = 0; i < 150; i++) {
            sender.sendVerification("u" + i + "@school.ac.kr", "http://x/verify?token=" + i);
        }
        assertThat(sender.sent()).hasSize(100);
        assertThat(sender.sent().get(0).toEmail()).isEqualTo("u50@school.ac.kr");
        assertThat(sender.sent().get(99).toEmail()).isEqualTo("u149@school.ac.kr");
    }
}
