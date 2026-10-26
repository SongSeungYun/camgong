package com.camgong.support;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/** 개설·수정 요청 본문 헬퍼. 값 하나만 바꿔 쓰도록 Map 으로 돌려준다. */
public final class GroupBuyBodies {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GroupBuyBodies() {
    }

    /** 총 수량 24, 개설자 수량 9, 마감 START+7일. quickstart 시나리오 1. */
    public static Map<String, Object> valid() {
        return valid(IntegrationTestBase.START.plus(Duration.ofDays(7)));
    }

    public static Map<String, Object> valid(Instant deadline) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "햇반 24개 공구");
        m.put("description", "같이 사요");
        m.put("itemName", "햇반 210g");
        m.put("unitPrice", 1200);
        m.put("totalQuantity", 24);
        m.put("deadline", iso(deadline));
        m.put("organizerQuantity", 9);
        m.put("category", "FOOD");
        m.put("itemUrl", "https://example.com/item/123");
        m.put("openChatUrl", "https://open.kakao.com/o/abc");
        return m;
    }

    public static String iso(Instant instant) {
        return instant.atOffset(ZoneOffset.ofHours(9)).toString();
    }

    public static String json(Map<String, Object> body) {
        return JSON.writeValueAsString(body);
    }

    public static Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.put(key, value);
        return copy;
    }
}
