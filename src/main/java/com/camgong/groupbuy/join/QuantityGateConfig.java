package com.camgong.groupbuy.join;

import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.camgong.groupbuy.GroupBuyRepository;

/**
 * camgong.quantity-gate 로 수량 게이트 구현체를 고른다(헌법 II 비교용): pessimistic(기본), conditional, optimistic, none.
 * none 은 일부러 틀리게 만든 구현이라 benchmark 프로파일에서만 허용한다. 알 수 없는 값은 기동 오류다.
 */
@Configuration
class QuantityGateConfig implements InitializingBean {

    private static final Set<String> ALLOWED = Set.of("pessimistic", "conditional", "optimistic", "none");

    private final Environment env;

    QuantityGateConfig(Environment env) {
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        String gate = env.getProperty("camgong.quantity-gate", "pessimistic");
        if (!ALLOWED.contains(gate)) {
            throw new IllegalStateException("camgong.quantity-gate 는 " + ALLOWED + " 중 하나여야 한다: " + gate);
        }
        if (gate.equals("none") && !env.acceptsProfiles(Profiles.of("benchmark"))) {
            throw new IllegalStateException("camgong.quantity-gate=none 은 benchmark 프로파일에서만 쓸 수 있다");
        }
    }

    @Bean
    @ConditionalOnProperty(name = "camgong.quantity-gate", havingValue = "pessimistic", matchIfMissing = true)
    QuantityGate pessimisticQuantityGate(GroupBuyRepository groupBuys) {
        return new PessimisticQuantityGate(groupBuys);
    }

    @Bean
    @ConditionalOnProperty(name = "camgong.quantity-gate", havingValue = "conditional")
    QuantityGate conditionalQuantityGate(JdbcClient jdbc) {
        return new ConditionalUpdateQuantityGate(jdbc);
    }

    @Bean
    @ConditionalOnProperty(name = "camgong.quantity-gate", havingValue = "optimistic")
    QuantityGate optimisticQuantityGate(GroupBuyRepository groupBuys) {
        return new OptimisticQuantityGate(groupBuys);
    }

    /** 프로파일 검사는 afterPropertiesSet 이 한다: benchmark 가 아니면 기동 오류. */
    @Bean
    @ConditionalOnProperty(name = "camgong.quantity-gate", havingValue = "none")
    QuantityGate noLockQuantityGate(JdbcClient jdbc) {
        return new NoLockQuantityGate(jdbc);
    }
}
