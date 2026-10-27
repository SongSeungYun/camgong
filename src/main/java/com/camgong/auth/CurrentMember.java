package com.camgong.auth;

/** 컨트롤러 인자로 받는 인증된 요청자. 이후 기능은 이것으로 요청자를 받는다. */
public record CurrentMember(Long id, String displayName) {
}
