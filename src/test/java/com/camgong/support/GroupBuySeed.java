package com.camgong.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;

/** 조회 테스트용 공구 시드. 상태·카테고리·마감을 마음대로 정해 SQL 로 넣는다(개설 API 의 검증을 거치지 않는다). */
public final class GroupBuySeed {

    private long organizerId = 1;
    private String title = "시드 공구";
    private String itemName = "시드 상품";
    private String category = "FOOD";
    private String status = "OPEN";
    private Instant deadline = IntegrationTestBase.START.plusSeconds(7 * 86400);
    private int unitPrice = 1200;
    private int total = 24;
    private int secured = 9;
    private Instant createdAt = IntegrationTestBase.START;

    private GroupBuySeed() {
    }

    public static GroupBuySeed groupBuy() {
        return new GroupBuySeed();
    }

    public GroupBuySeed organizer(long id) {
        this.organizerId = id;
        return this;
    }

    public GroupBuySeed title(String v) {
        this.title = v;
        return this;
    }

    public GroupBuySeed item(String v) {
        this.itemName = v;
        return this;
    }

    public GroupBuySeed category(String v) {
        this.category = v;
        return this;
    }

    public GroupBuySeed status(String v) {
        this.status = v;
        return this;
    }

    public GroupBuySeed deadline(Instant v) {
        this.deadline = v;
        return this;
    }

    public GroupBuySeed price(int v) {
        this.unitPrice = v;
        return this;
    }

    public GroupBuySeed quantities(int total, int secured) {
        this.total = total;
        this.secured = secured;
        return this;
    }

    public GroupBuySeed createdAt(Instant v) {
        this.createdAt = v;
        return this;
    }

    public long insert(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO group_buy (organizer_id, title, description, item_name, unit_price, total_quantity, "
                + "secured_quantity, deadline, category, item_url, open_chat_url, status, version, created_at, updated_at) "
                + "VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, 'https://example.com/item', 'https://open.kakao.com/o/abc', ?, 0, ?, ?)",
                organizerId, title, itemName, unitPrice, total, secured, utc(deadline), category, status, utc(createdAt),
                utc(createdAt));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private static LocalDateTime utc(Instant t) {
        return LocalDateTime.ofInstant(t, ZoneOffset.UTC);
    }
}
