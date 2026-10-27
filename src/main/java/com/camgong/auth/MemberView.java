package com.camgong.auth;

/** 다른 회원에게도 보이는 회원 정보는 표시 이름뿐이다. 이메일을 두지 않는다. */
public record MemberView(Long id, String displayName) {
}
