package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import com.camgong.support.IntegrationTestBase;

/** Secure 쿠키는 기본이고, 로컬 개발용 local 프로파일에서만 꺼진다. */
@ActiveProfiles("local")
class LocalProfileCookieIT extends IntegrationTestBase {

    @Autowired
    AuthProperties props;

    @Autowired
    SessionService sessions;

    @Test
    void localProfileTurnsSecureOff() {
        assertThat(props.cookieSecure()).isFalse();
        assertThat(sessions.cookie("abc").isSecure()).isFalse();
    }
}
