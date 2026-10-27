package com.camgong.groupbuy.join;

import java.util.concurrent.atomic.LongAdder;

/** 벤치마크가 읽는 앱 쪽 지표(재시도 수, BUSY 수, 모집 완료 이벤트 발행 시도 수 — 커밋 전에 세므로 롤백된 시도도 포함한다). 값이 세어질 뿐 동작에는 영향이 없다. */
final class BenchCounters {

    static final LongAdder RETRIES = new LongAdder();
    static final LongAdder BUSY = new LongAdder();
    static final LongAdder COMPLETED_EVENTS = new LongAdder();

    private BenchCounters() {
    }

    static void reset() {
        RETRIES.reset();
        BUSY.reset();
        COMPLETED_EVENTS.reset();
    }
}
