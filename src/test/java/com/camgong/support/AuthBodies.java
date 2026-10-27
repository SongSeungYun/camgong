package com.camgong.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** 가입·인증 요청 본문 헬퍼. */
public final class AuthBodies {

    private AuthBodies() {
    }

    /** 가입 신청 본문: 이메일 하나(변경 B2). */
    public static Map<String, Object> signup(String email) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", email);
        return m;
    }

    /** 인증 본문: 이름·비밀번호·동의는 인증 단계에서 정한다. */
    public static Map<String, Object> verify(String token, String displayName, String password) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        m.put("displayName", displayName);
        m.put("password", password);
        m.put("consent", true);
        m.put("consentVersion", "v1");
        return m;
    }

    public static Map<String, Object> verify(String token, String displayName) {
        return verify(token, displayName, "password1");
    }

    public static Map<String, Object> verify(String token) {
        return verify(token, "새회원", "password1");
    }

    public static Map<String, Object> login(String email, String password) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", email);
        m.put("password", password);
        return m;
    }
}
