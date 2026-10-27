package com.camgong.common;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.camgong.member.MemberRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 본문 바인딩·검증(400)보다 먼저 회원을 확인해 401 이 항상 이긴다.
 * ponytail: 임시 인증(X-Member-Id), 4단계에서 로그인으로 교체
 */
@Component
public class MemberHeaderInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final MemberRepository members;

    public MemberHeaderInterceptor(MemberRepository members) {
        this.members = members;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("X-Member-Id");
        try {
            if (header != null && members.existsById(Long.valueOf(header.trim()))) {
                return true;
            }
        } catch (NumberFormatException ignored) {
            // 숫자가 아니면 아래에서 401
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "로그인이 필요합니다.");
    }
}
