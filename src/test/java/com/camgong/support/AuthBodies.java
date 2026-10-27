package com.camgong.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** 가입·인증 요청 본문 헬퍼. */
public final class AuthBodies {

    private AuthBodies() {
    }

    public static Map<String, Object> signup(String email, String displayName, String password) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", email);
        m.put("displayName", displayName);
        m.put("password", password);
        m.put("consent", true);
        m.put("consentVersion", "v1");
        return m;
    }

    public static Map<String, Object> signup(String email) {
        return signup(email, "새회원", "password1");
    }

    public static Map<String, Object> verify(String token) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        return m;
    }

    public static Map<String, Object> verify(String token, String displayName) {
        Map<String, Object> m = verify(token);
        m.put("displayName", displayName);
        return m;
    }
}
