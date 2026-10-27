package com.camgong.groupbuy.join;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.camgong.groupbuy.GroupBuyStatus;

/**
 * 벤치마크 전용, 일부러 틀리게 만든 구현: 일반 SELECT 로 읽고 자바에서 검사한 뒤 "읽은 값 + delta" 를 쓴다.
 * 동시에 오면 갱신 손실로 secured_quantity ≠ Σ 수량이 생기는 것이 기대 동작이다(락과 조건의 가치를 보이는 대조군).
 * benchmark 프로파일에서만 켤 수 있다(QuantityGateConfig).
 */
class NoLockQuantityGate implements QuantityGate {

    private final JdbcClient jdbc;

    NoLockQuantityGate(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Row(int secured, int total, int price, GroupBuyStatus status, Instant deadline) {
    }

    @Override
    public GateResult reserve(long groupBuyId, int delta, Integer expectedUnitPrice, Instant now) {
        Row r = jdbc.sql("SELECT secured_quantity, total_quantity, unit_price, status, deadline FROM group_buy WHERE id = :id")
                .param("id", groupBuyId)
                .query((rs, i) -> new Row(rs.getInt("secured_quantity"), rs.getInt("total_quantity"),
                        rs.getInt("unit_price"), GroupBuyStatus.valueOf(rs.getString("status")),
                        rs.getObject("deadline", LocalDateTime.class).toInstant(ZoneOffset.UTC)))
                .optional().orElse(null);
        if (r == null || r.status() == GroupBuyStatus.CANCELLED) {
            return GateResult.notFound();
        }
        if (r.status() != GroupBuyStatus.OPEN) {
            return GateResult.notJoinable(r.status().name());
        }
        if (!r.deadline().isAfter(now)) {
            return GateResult.notJoinable("DEADLINE_PASSED");
        }
        if (expectedUnitPrice != null && expectedUnitPrice != r.price()) {
            return GateResult.priceChanged(r.price());
        }
        if (r.secured() + delta > r.total()) {
            return GateResult.insufficient(r.secured(), r.total());
        }
        int secured = r.secured() + delta;
        boolean completed = secured == r.total();
        jdbc.sql("UPDATE group_buy SET secured_quantity = :secured, status = :status, version = version + 1, "
                + "updated_at = :now WHERE id = :id")
                .param("secured", secured).param("status", completed ? "COMPLETED" : "OPEN")
                .param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC)).param("id", groupBuyId).update();
        return GateResult.reserved(secured, r.total(), r.price(), completed);
    }
}
