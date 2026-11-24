package com.camgong.auth;

/** 커밋 뒤 재설정 메일을 보내라는 이벤트(011). 가입한 회원에게만 낸다. */
record PasswordResetMailRequested(String email, String token) {

    /** 토큰은 로그에 남기지 않는다. */
    @Override
    public String toString() {
        return "PasswordResetMailRequested[email=" + VerificationMailListener.mask(email) + ", token=***]";
    }
}
