package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.camgong.member.Member;
import com.camgong.member.MemberRepository;

import jakarta.servlet.http.Cookie;

/** 010: 탈퇴한 회원 세션을 지우는 것은 최선 노력이다. 지우다 교착에 져도 요청은 500 이 아니라 비로그인으로 이어진다. */
class AuthFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aFailedRevokeOfAWithdrawnMembersSessionStillLeavesTheRequestUnauthenticated() throws Exception {
        SessionService sessions = mock(SessionService.class);
        MemberRepository members = mock(MemberRepository.class);
        Member withdrawn = mock(Member.class);
        when(withdrawn.isWithdrawn()).thenReturn(true);
        when(sessions.resolveSession("raw")).thenReturn(Optional.of(new SessionService.Resolved(7L, false)));
        when(members.findById(7L)).thenReturn(Optional.of(withdrawn));
        doThrow(new CannotAcquireLockException("Deadlock found")).when(sessions).revoke("raw");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setCookies(new Cookie("camgong_session", "raw"));
        MockFilterChain chain = new MockFilterChain();
        new AuthFilter(sessions, members).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("다음 필터로 넘어간다").isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
