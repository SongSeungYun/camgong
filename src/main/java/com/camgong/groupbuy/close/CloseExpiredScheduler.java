package com.camgong.groupbuy.close;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 마감 확정을 실행한다. camgong.scheduler.close.enabled=false 면 등록되지 않는다.
 * fixedDelay(이전 실행이 끝난 뒤 간격을 센다)라서 같은 인스턴스의 실행이 겹치지 않고, 앱 시작 직후 바로 한 번 돈다.
 * 인스턴스 사이의 겹침은 건별 SKIP LOCKED 선점이 막는다.
 */
// ponytail: 재시작 직후 밀린 공구는 시작 직후 실행과 이후 주기 실행이 나눠 처리한다
@Component
@ConditionalOnProperty(name = "camgong.scheduler.close.enabled", havingValue = "true", matchIfMissing = true)
class CloseExpiredScheduler {

    private static final Logger log = LoggerFactory.getLogger(CloseExpiredScheduler.class);

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "camgong.scheduler.close.enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {
    }

    private final GroupBuyCloseService service;

    CloseExpiredScheduler(GroupBuyCloseService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${camgong.scheduler.close.interval:30s}", initialDelay = 0)
    void run() {
        try {
            service.closeExpired();
        } catch (RuntimeException e) {
            // 후보 조회 같은 실행 전체의 실패가 스케줄을 멈추지 않게 한다. 다음 주기에 다시 시도한다.
            log.warn("close-run-failed reason={}", e.toString());
        }
    }
}
