package com.camgong.auth;

public record WithdrawRequest(String password) {

    /** 로그·예외 메시지에 비밀번호가 새지 않도록 값을 가린다. */
    @Override
    public String toString() {
        return "WithdrawRequest[password=***]";
    }
}
