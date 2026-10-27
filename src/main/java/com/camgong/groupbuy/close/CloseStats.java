package com.camgong.groupbuy.close;

/** 한 번의 확정 실행 결과. rounds 는 후보가 있었던 라운드 수다. */
public record CloseStats(int closed, int skipped, int failed, int rounds, long durationMs) {
}
