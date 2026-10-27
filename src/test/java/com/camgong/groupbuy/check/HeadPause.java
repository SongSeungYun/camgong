package com.camgong.groupbuy.check;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.camgong.groupbuy.join.JoinQueryRepository;

/**
 * 체크 요청이 접근 검사(첫 읽기 = 트랜잭션 스냅샷 고정)를 마친 직후에 멈춰 세운다. 그 사이 다른 요청이 같은 값을 커밋하게 해
 * "내 UPDATE 가 아무것도 바꾸지 못하는" 경쟁을 결정적으로 만든다. arm() 하기 전에는 그대로 통과한다.
 */
final class HeadPause {

    private static final AtomicReference<Pause> ACTIVE = new AtomicReference<>();

    private HeadPause() {
    }

    static final class Pause {
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);

        void awaitReached() throws InterruptedException {
            if (!reached.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("요청이 접근 검사 뒤에서 멈추지 않았다");
            }
        }

        void release() {
            proceed.countDown();
        }
    }

    static Pause arm() {
        Pause p = new Pause();
        ACTIVE.set(p);
        return p;
    }

    static void disarm() {
        ACTIVE.set(null);
    }

    @TestConfiguration
    static class Config {
        @Bean
        static BeanPostProcessor pauseAfterHead() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof JoinQueryRepository repo)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(JoinQueryRepository.class.getClassLoader(), new Class<?>[] { JoinQueryRepository.class },
                            (proxy, method, args) -> {
                                Object result;
                                try {
                                    result = method.invoke(repo, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                                Pause pause = ACTIVE.get();
                                if (method.getName().equals("findHead") && pause != null && pause.reached.getCount() > 0) {
                                    pause.reached.countDown();
                                    pause.proceed.await(15, TimeUnit.SECONDS);
                                }
                                return result;
                            });
                }
            };
        }
    }
}
