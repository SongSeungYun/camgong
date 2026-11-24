package com.camgong.retention;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;

/** 012 T008: 보관 기간이 1년보다 짧으면 기동이 실패한다. 단위 없는 숫자("365")는 Spring 이 밀리초로 읽는다. */
class RetentionCleanupSettingsTest {

    private static RetentionCleanup with(Duration endedFor) {
        return new RetentionCleanup(null, null, null, endedFor);
    }

    @Test
    void anythingShorterThanAYearIsRefused() {
        Duration unitless = ApplicationConversionService.getSharedInstance().convert("365", Duration.class);
        for (Duration bad : new Duration[] { unitless, Duration.ZERO, Duration.ofDays(-1), Duration.ofDays(364), null }) {
            assertThatThrownBy(() -> with(bad)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("camgong.retention.ended-for");
        }
        assertThatNoException().isThrownBy(() -> with(Duration.ofDays(365)));
    }
}
