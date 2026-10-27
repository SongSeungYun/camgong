package com.camgong.auth;

/** 인증(가입 완료·로그인) 성공 결과: 응답 본문과 쿠키에 담을 세션 원문 토큰. */
public record Authenticated(AuthResponse response, String sessionToken) {

    /** 세션 원문 토큰이 로그에 남지 않도록 가린다. */
    @Override
    public String toString() {
        return "Authenticated[response=" + response + ", sessionToken=***]";
    }
}
