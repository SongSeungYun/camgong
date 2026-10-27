package com.camgong.auth;

/**
 * 인증 링크를 누른 사람이 정하는 가입 입력(변경 B2). 값 검증은 토큰 확인 뒤에 서비스가 한다 — 토큰 오류가 본문 오류보다
 * 먼저 나가야 하고, 본문 오류는 토큰을 소진하지 않아야 하기 때문에 @Valid 를 쓰지 않는다.
 */
public record VerifyRequest(String token, String displayName, String password, Boolean consent, String consentVersion) {

    /** 로그·예외 메시지에 토큰과 비밀번호가 새지 않도록 값을 가린다. */
    @Override
    public String toString() {
        return "VerifyRequest[token=***, displayName=" + displayName + ", password=***, consent=" + consent
                + ", consentVersion=" + consentVersion + "]";
    }
}
