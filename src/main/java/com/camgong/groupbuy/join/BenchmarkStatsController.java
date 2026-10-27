package com.camgong.groupbuy.join;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** benchmark 프로파일에서만 뜬다: 측정 스크립트가 시나리오 전후에 앱 지표를 읽고 지운다. */
@Profile("benchmark")
@RestController
@RequestMapping("/bench")
class BenchmarkStatsController {

    @GetMapping("/stats")
    Map<String, Long> stats() {
        return Map.of("retries", BenchCounters.RETRIES.sum(), "busy", BenchCounters.BUSY.sum(),
                "completedEvents", BenchCounters.COMPLETED_EVENTS.sum());
    }

    @PostMapping("/reset")
    void reset() {
        BenchCounters.reset();
    }
}
