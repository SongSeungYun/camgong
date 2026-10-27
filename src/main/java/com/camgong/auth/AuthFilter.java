package com.camgong.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.camgong.member.MemberRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 쿠키 camgong_session → 세션 → 회원을 해석한다. 요청자를 알려 주는 헤더는 읽지 않는다(FR-014). */
class AuthFilter extends OncePerRequestFilter {

    private static final String LOGOUT_PATH = "/api/auth/logout";

    private final SessionService sessions;
    private final MemberRepository members;

    AuthFilter(SessionService sessions, MemberRepository members) {
        this.sessions = sessions;
        this.members = members;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = sessionCookie(request);
        if (raw != null) {
            sessions.resolveSession(raw).ifPresent(session -> members.findById(session.memberId()).ifPresent(m -> {
                var principal = new CurrentMember(m.getId(), m.getName());
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
                // 서버의 14일 슬라이딩에 쿠키 수명도 맞춘다. 갱신된 요청에서만 다시 내려 쓰기가 늘지 않게 한다.
                // 로그아웃은 삭제 쿠키를 직접 내려주므로 건너뛴다.
                if (session.refreshed() && !LOGOUT_PATH.equals(request.getRequestURI())) {
                    response.addHeader(HttpHeaders.SET_COOKIE, sessions.cookie(raw).toString());
                }
            }));
        }
        chain.doFilter(request, response);
    }

    private static String sessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (SessionService.COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
