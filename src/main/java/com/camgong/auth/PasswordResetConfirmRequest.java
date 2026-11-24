package com.camgong.auth;

/** 새 비밀번호 정하기(011). 토큰 오류가 비밀번호 오류보다 먼저 나가야 해서 @Valid 를 쓰지 않는다(VerifyRequest 와 같다). */
public record PasswordResetConfirmRequest(String token, String password) {

    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[token=***, password=***]";
    }
}
