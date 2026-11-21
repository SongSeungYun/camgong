package com.camgong.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기동 때 탈퇴 장부를 다시 적용한다(010 결정 5-2). 복구 절차는 백엔드 중지 → 덤프 넣기 → 백엔드 시작 → 로그의 reanonymized=N 확인이다.
 * 웹 서버가 요청을 받기 전(모든 싱글턴을 만든 직후)에 돈다: 되살아난 회원이 그 사이 로그인하지 못하게. 실패하면 기동도 실패한다(복구가 덜 된 채로 뜨지 않게).
 * 8일 지난 줄은 적용 전에, 그리고 하루 한 번 지운다(T034).
 */
@Component
public class WithdrawLedgerReplay implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(WithdrawLedgerReplay.class);

    private final WithdrawLedger ledger;
    private final WithdrawService withdrawals;
    private final Clock clock;

    WithdrawLedgerReplay(WithdrawLedger ledger, WithdrawService withdrawals, Clock clock) {
        this.ledger = ledger;
        this.withdrawals = withdrawals;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        replay();
    }

    /** @return 다시 익명화한 회원 수 */
    public int replay() {
        ledger.prune(Instant.now(clock));
        int count = 0;
        for (WithdrawLedger.Entry entry : ledger.entries()) {
            Optional<List<Long>> open = withdrawals.reanonymise(entry);
            if (open.isEmpty()) {
                continue;                                      // 이미 탈퇴했거나, 덤프에 없거나, 같은 번호의 다른 회원
            }
            count++;
            if (!open.get().isEmpty()) {
                // 되살아난 동안 모집 중 공구에 걸려 있다: 공구는 그대로 두고 운영자가 본다.
                log.warn("reanonymized-with-open-groupbuys memberId={} groupBuyIds={}", entry.memberId(), open.get());
            }
        }
        log.info("reanonymized={}", count);
        return count;
    }

    @Configuration
    @EnableScheduling
    static class Scheduling {

        private final WithdrawLedger ledger;
        private final Clock clock;

        Scheduling(WithdrawLedger ledger, Clock clock) {
            this.ledger = ledger;
            this.clock = clock;
        }

        @Scheduled(fixedDelayString = "P1D", initialDelayString = "P1D")
        void prune() {
            try {
                ledger.prune(Instant.now(clock));
            } catch (RuntimeException e) {
                log.warn("withdraw-ledger-prune-failed reason={}", e.getClass().getSimpleName());
            }
        }
    }
}
