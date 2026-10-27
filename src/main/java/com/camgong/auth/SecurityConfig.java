package com.camgong.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.camgong.member.MemberRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * 쿠키 세션 인증(research 결정 1·10). 로그인·가입 진입점과 공개 목록만 열고 나머지 /api/** 는 인증이 필요하다.
 * CSRF 토큰·폼 로그인·HTTP Basic 은 끄고 Origin 검사로 대신한다. 세션은 DB 에 있어 서버 세션 정책은 STATELESS.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SessionService sessions, MemberRepository members,
            AuthProperties props, JsonMapper mapper) throws Exception {
        AuthFilter authFilter = new AuthFilter(sessions, members);
        OriginCheckFilter originFilter = new OriginCheckFilter(props.allowedOrigins(), mapper);

        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/signup/resend",
                                "/api/auth/verify", "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/groupbuys").permitAll()  // 003 이 목록을 구현하면 열린다
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) ->
                        ProblemResponses.write(response, mapper, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                                "로그인이 필요합니다.")))
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(originFilter, AuthFilter.class);
        return http.build();
    }
}
