package com.camgong.groupbuy.join;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 구현체 선택 설정의 기동 검증: 알 수 없는 값은 오류, none 은 benchmark 프로파일에서만. */
class QuantityGateConfigTest {

    private static QuantityGateConfig config(String gate, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        if (gate != null) {
            env.setProperty("camgong.quantity-gate", gate);
        }
        env.setActiveProfiles(profiles);
        return new QuantityGateConfig(env);
    }

    @Test
    void knownValuesAndTheDefaultAreAccepted() {
        for (String g : new String[] { null, "pessimistic", "conditional", "optimistic" }) {
            assertThatCode(() -> config(g).afterPropertiesSet()).doesNotThrowAnyException();
        }
    }

    @Test
    void unknownOrMisspelledValueFailsStartup() {
        for (String g : new String[] { "lock", "", "NONE", "Pessimistic" }) {
            assertThatThrownBy(() -> config(g, "benchmark").afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void noneIsOnlyAllowedUnderTheBenchmarkProfile() {
        assertThatThrownBy(() -> config("none").afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config("none", "test").afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> config("none", "benchmark").afterPropertiesSet()).doesNotThrowAnyException();
    }
}
