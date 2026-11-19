package com.camgong.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.camgong.common.ApiException;

/**
 * 새 참여(004)·새 개설(001)이 트랜잭션 첫 문장으로 회원 행에 공유 잠금을 건다(010 plan 결정 3). 탈퇴는 같은 행을 FOR UPDATE 로
 * 잡으므로 둘이 겹치면 한쪽이 기다린다: 탈퇴가 먼저면 여기서 401, 참여·개설이 먼저면 탈퇴가 그 행을 보고 409 다.
 * 잠금 순서는 항상 member → group_buy 다.
 */
@Component
public class ActiveMember {

    private final JdbcClient jdbc;

    ActiveMember(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 트랜잭션 안에서 부른다. 탈퇴했거나 없으면 로그인이 풀린 것과 같은 401. */
    public void lockShared(long memberId) {
        boolean active = jdbc.sql("SELECT withdrawn_at IS NULL FROM member WHERE id = :id FOR SHARE")
                .param("id", memberId).query(Boolean.class).optional().orElse(false);
        if (!active) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다.");
        }
    }
}
