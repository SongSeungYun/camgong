package com.camgong.support;

import org.springframework.test.web.servlet.MvcResult;

/** 응답의 Set-Cookie 에서 세션 쿠키 값을 꺼낸다. */
public final class Cookies {

    private Cookies() {
    }

    public static String sessionOf(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        if (header == null || !header.startsWith("camgong_session=")) {
            return null;
        }
        int end = header.indexOf(';');
        return header.substring("camgong_session=".length(), end < 0 ? header.length() : end);
    }
}
