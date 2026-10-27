package com.camgong.groupbuy.check;

/** 체크표 요약. 필터와 무관하게 개설자를 뺀 전체 참여자 기준이다. */
public record CheckSummary(int participantCount, int paidCount, int receivedCount, long amountDue, long amountConfirmed) {
}
