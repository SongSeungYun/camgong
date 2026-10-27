package com.camgong.groupbuy.close;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.camgong.groupbuy.GroupBuyRepository;

/** 마감이 지난 미충족 공구를 모집 실패로 확정하는 한 번의 실행(스케줄러나 테스트가 호출한다). */
@Service
public class GroupBuyCloseService {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyCloseService.class);

    private final GroupBuyRepository groupBuys;
    private final GroupBuyCloser closer;
    private final CloseProperties props;
    private final Clock clock;

    GroupBuyCloseService(GroupBuyRepository groupBuys, GroupBuyCloser closer, CloseProperties props, Clock clock) {
        this.groupBuys = groupBuys;
        this.closer = closer;
        this.props = props;
        this.clock = clock;
    }

    /**
     * 후보를 batch-size 씩 조회해 건별로 확정한다. 마감이 이른 공구부터. 이번 실행에서 이미 시도한 id 는 다음 조회에서 뺀다 —
     * 계속 실패하거나 다른 인스턴스에 잡혀 건너뛰는 공구가 후보 조회의 앞쪽을 차지해 뒤쪽 후보를 굶기지 않게 한다.
     * 라운드 반복 조건: 시도하지 않은 후보가 없으면 끝, 최대 max-rounds. 한 공구의 예외는 세어 기록하고 다음 공구로 넘어간다(FR-008).
     */
    public CloseStats closeExpired() {
        long start = System.nanoTime();
        int closed = 0;
        int skipped = 0;
        int failed = 0;
        int rounds = 0;
        Set<Long> attempted = new HashSet<>();

        for (int round = 0; round < props.maxRounds(); round++) {
            Instant now = Instant.now(clock);
            Instant cutoff = now.minus(props.margin());
            // 빈 목록의 NOT IN 은 쓸 수 없어 아무 공구와도 겹치지 않는 -1 을 넣는다.
            List<Long> candidates = groupBuys.findCloseCandidates(cutoff, attempted.isEmpty() ? List.of(-1L) : attempted,
                    Limit.of(props.batchSize()));
            if (candidates.isEmpty()) {
                break;
            }
            attempted.addAll(candidates);
            rounds++;
            for (Long id : candidates) {
                try {
                    if (closer.closeOne(id, cutoff, now) == GroupBuyCloser.Outcome.CLOSED) {
                        closed++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("close-failed groupBuyId={} reason={}", id, e.toString());
                }
            }
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        if (rounds > 0) {
            log.info("close-run closed={} skipped={} failed={} rounds={} durationMs={}", closed, skipped, failed, rounds,
                    durationMs);
        }
        return new CloseStats(closed, skipped, failed, rounds, durationMs);
    }
}
