package com.camgong.auth;

import java.util.Locale;

/** 이메일 키·표시 이름 키 공용 정규화: 앞뒤 공백 제거 + 소문자(로캘 무관). 한글은 그대로다. */
public final class Keys {

    private Keys() {
    }

    public static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
