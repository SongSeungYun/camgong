package com.camgong.groupbuy.join;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.camgong.groupbuy.GroupBuyStatus;

/**
 * 한 문장의 조건부 UPDATE 로 선점한다(research 결정 5): 상태·마감·가격·남은 수량 조건을 WHERE 가 판정하므로 락을 따로 잡지 않고
 * 영향 행 1이면 성공, 0이면 거부다. 같은 공구의 UPDATE 는 InnoDB 행 잠금으로 직렬화되고, 기다린 쪽은 최신 값으로 조건을 다시 본다.
 * JDBC 의 useAffectedRows=true 와 SET 절의 왼쪽→오른쪽 평가 순서(status 가 갱신된 secured_quantity 를 본다)에 기댄다.
 */
// -- ponytail: MySQL 전용 문법(IF, SET 평가 순서). 직접 UPDATE 도 version 을 올려 낙관적 방식과 섞여도 안전하다
class ConditionalUpdateQuantityGate implements QuantityGate {

    private final JdbcClient jdbc;

    ConditionalUpdateQuantityGate(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public GateResult reserve(long groupBuyId, int delta, Integer expectedUnitPrice, Instant now) {
        LocalDateTime at = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        // 취소(expectedUnitPrice = null)는 가격 조건을 뺀다.
        String priceCondition = expectedUnitPrice == null ? "" : " AND unit_price = :price";
        var update = jdbc.sql("""
                UPDATE group_buy SET
                    secured_quantity = secured_quantity + :delta,
                    status = IF(secured_quantity = total_quantity, 'COMPLETED', status),
                    version = version + 1, updated_at = :now
                WHERE id = :id AND status = 'OPEN' AND deadline > :now
                    AND secured_quantity + :delta <= total_quantity""" + priceCondition)
                .param("delta", delta).param("now", at).param("id", groupBuyId);
        if (expectedUnitPrice != null) {
            update = update.param("price", expectedUnitPrice);
        }
        if (update.update() == 1) {
            return jdbc.sql("SELECT secured_quantity, total_quantity, unit_price, status FROM group_buy WHERE id = :id")
                    .param("id", groupBuyId)
                    .query((rs, i) -> GateResult.reserved(rs.getInt("secured_quantity"), rs.getInt("total_quantity"),
                            rs.getInt("unit_price"), "COMPLETED".equals(rs.getString("status"))))
                    .single();
        }
        return diagnose(groupBuyId, delta, expectedUnitPrice, at);
    }

    /**
     * 0행인 이유를 가른다. 잠금 읽기(FOR SHARE)라서 트랜잭션 스냅샷이 아니라 방금 커밋된 최신 행을 본다.
     * UPDATE 와 진단 사이에 상태가 풀렸다면(통과 조건인데 0행이었다면) 처음부터 다시 한다.
     */
    private GateResult diagnose(long groupBuyId, int delta, Integer expectedUnitPrice, LocalDateTime now) {
        return jdbc.sql("SELECT secured_quantity, total_quantity, unit_price, status, deadline FROM group_buy "
                + "WHERE id = :id FOR SHARE")
                .param("id", groupBuyId)
                .query((rs, i) -> {
                    GroupBuyStatus status = GroupBuyStatus.valueOf(rs.getString("status"));
                    int secured = rs.getInt("secured_quantity");
                    int total = rs.getInt("total_quantity");
                    int price = rs.getInt("unit_price");
                    if (status == GroupBuyStatus.CANCELLED) {
                        return GateResult.notFound();
                    }
                    if (status == GroupBuyStatus.COMPLETED || status == GroupBuyStatus.FAILED) {
                        return GateResult.notJoinable(status.name());
                    }
                    if (!rs.getObject("deadline", LocalDateTime.class).isAfter(now)) {
                        return GateResult.notJoinable("DEADLINE_PASSED");
                    }
                    if (expectedUnitPrice != null && expectedUnitPrice != price) {
                        return GateResult.priceChanged(price);
                    }
                    if (secured + delta > total) {
                        return GateResult.insufficient(secured, total);
                    }
                    throw new RetryableConflict("조건을 만족하는데 갱신되지 않았다", null);
                })
                .optional()
                .orElseGet(GateResult::notFound);
    }
}
