package com.camgong.groupbuy.join;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 같은 회원의 동시 이중 요청이 "사전 검사는 둘 다 낡은 값으로 통과한 뒤 게이트에서 직렬화"되도록 결정적으로 겹친다.
 * 시작 래치만으로는 한 요청이 먼저 끝나 버려 보호 블록(UNIQUE 재시도, 읽은 뒤 바뀐 참여 행 검사)이 실행되지 않을 수 있다.
 * arm(n) 하면 게이트에 n 번째 호출이 들어올 때까지 모두 게이트 앞에서 기다린다. 이후 재시도 호출은 막지 않는다.
 */
final class Overlap {

    private static final AtomicReference<CountDownLatch> LATCH = new AtomicReference<>();

    private Overlap() {
    }

    static void arm(int callers) {
        LATCH.set(new CountDownLatch(callers));
    }

    static void disarm() {
        LATCH.set(null);
    }

    @TestConfiguration
    static class Config {
        @Bean
        static BeanPostProcessor overlappingGates() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof QuantityGate gate)) {
                        return bean;
                    }
                    return (QuantityGate) (groupBuyId, delta, price, now) -> {
                        CountDownLatch latch = LATCH.get();
                        if (latch != null) {
                            latch.countDown();
                            try {
                                latch.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        return gate.reserve(groupBuyId, delta, price, now);
                    };
                }
            };
        }
    }
}
