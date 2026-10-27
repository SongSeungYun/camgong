package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.camgong.support.IntegrationTestBase;

/** T003: 설정 바인딩과 PasswordEncoder 빈 */
class AuthConfigIT extends IntegrationTestBase {

    @Autowired AuthProperties props;
    @Autowired PasswordEncoder encoder;

    @Test
    void bindsContractDefaults() {
        assertThat(props.allowedEmailDomains()).containsExactly("school.ac.kr");
        assertThat(props.sessionTtl()).isEqualTo(Duration.ofDays(14));
        assertThat(props.sessionTouchInterval()).isEqualTo(Duration.ofMinutes(10));
        assertThat(props.verifyTokenTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(props.mail().resendInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(props.mail().dailyLimit()).isEqualTo(5);
        assertThat(props.login().maxFailures()).isEqualTo(5);
        assertThat(props.login().lockDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(props.cookieSecure()).isFalse();
    }

    @Test
    void encoderHashesAndMatches() {
        String hash = encoder.encode("password1");
        assertThat(hash).startsWith("$2").isNotEqualTo("password1");
        assertThat(encoder.matches("password1", hash)).isTrue();
        assertThat(encoder.matches("password2", hash)).isFalse();
    }
}
