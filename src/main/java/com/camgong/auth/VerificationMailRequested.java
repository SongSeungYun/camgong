package com.camgong.auth;

/** 커밋 뒤 인증 메일을 보내라는 이벤트. 롤백된 신청은 메일이 나가지 않는다. */
record VerificationMailRequested(String email, String token) {

    /** 토큰은 로그에 남기지 않는다. */
    @Override
    public String toString() {
        return "VerificationMailRequested[email=" + VerificationMailListener.mask(email) + ", token=***]";
    }
}
