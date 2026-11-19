package com.camgong.auth;

import com.camgong.member.Member;

/** 로그인한 본인에게 주는 회원 정보. 이메일은 두지 않는다. school 은 학교 키(009). */
public record MemberView(Long id, String displayName, String school) {

    public static MemberView of(Member m) {
        return new MemberView(m.getId(), m.getName(), m.getSchool());
    }
}
