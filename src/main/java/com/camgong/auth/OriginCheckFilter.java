package com.camgong.auth;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * CSRF 대신 쓰는 출처 확인: 상태를 바꾸는 요청의 Origin(없으면 Referer 의 출처)이 요청과 같은 출처이거나
 * 허용 목록에 있어야 한다. 둘 다 없으면 통과한다(브라우저가 아닌 클라이언트, 쿠키는 SameSite=Lax 가 막는다).
 * 리버스 프록시 뒤에서 호스트가 달라지면 camgong.auth.allowed-origins 에 프론트 출처를 적는다.
 */
class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> CHANGING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final List<String> allowedOrigins;
    private final JsonMapper mapper;

    OriginCheckFilter(List<String> allowedOrigins, JsonMapper mapper) {
        this.allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (CHANGING.contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin == null) {
                origin = originOf(request.getHeader("Referer"));
            }
            if (origin != null && !isAllowed(normalize(origin), request)) {
                ProblemResponses.write(response, mapper, HttpStatus.FORBIDDEN, "FORBIDDEN_ORIGIN",
                        "허용되지 않은 출처의 요청입니다.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(String origin, HttpServletRequest request) {
        return origin.equals(selfOrigin(request))
                || allowedOrigins.stream().map(OriginCheckFilter::normalize).anyMatch(origin::equals);
    }

    private static String selfOrigin(HttpServletRequest request) {
        return normalize(request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort());
    }

    /** 스킴·호스트는 소문자로, 기본 포트(80/443)는 지워 같은 출처가 같은 문자열이 되게 한다. */
    static String normalize(String origin) {
        try {
            URI uri = URI.create(origin.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return origin;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            boolean defaultPort = port == -1 || port == 80 && "http".equals(scheme) || port == 443 && "https".equals(scheme);
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
        } catch (IllegalArgumentException e) {
            return origin;
        }
    }

    private static String originOf(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "invalid";
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException e) {
            return "invalid";
        }
    }
}
