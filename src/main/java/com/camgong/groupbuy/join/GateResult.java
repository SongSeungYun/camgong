package com.camgong.groupbuy.join;

/**
 * 수량 게이트의 결과. 성공이면 갱신 후 값(확보·총 수량, 개당 가격, 모집 완료 여부)을, 거부면 사유를 담는다.
 * 거부 사유별 부가 정보: PRICE_CHANGED 는 unitPrice(현재 가격), INSUFFICIENT_QUANTITY 는 total - secured(남은 수량),
 * NOT_JOINABLE 은 reason(DEADLINE_PASSED / COMPLETED / FAILED).
 */
public record GateResult(Outcome outcome, int securedQuantity, int totalQuantity, int unitPrice, boolean completed,
        String reason) {

    public enum Outcome {
        RESERVED, NOT_FOUND, NOT_JOINABLE, PRICE_CHANGED, INSUFFICIENT_QUANTITY, BUSY
    }

    public static GateResult reserved(int secured, int total, int unitPrice, boolean completed) {
        return new GateResult(Outcome.RESERVED, secured, total, unitPrice, completed, null);
    }

    public static GateResult notFound() {
        return new GateResult(Outcome.NOT_FOUND, 0, 0, 0, false, null);
    }

    public static GateResult notJoinable(String reason) {
        return new GateResult(Outcome.NOT_JOINABLE, 0, 0, 0, false, reason);
    }

    public static GateResult priceChanged(int currentUnitPrice) {
        return new GateResult(Outcome.PRICE_CHANGED, 0, 0, currentUnitPrice, false, null);
    }

    public static GateResult insufficient(int secured, int total) {
        return new GateResult(Outcome.INSUFFICIENT_QUANTITY, secured, total, 0, false, null);
    }

    public static GateResult busy() {
        return new GateResult(Outcome.BUSY, 0, 0, 0, false, null);
    }

    public boolean isReserved() {
        return outcome == Outcome.RESERVED;
    }
}
