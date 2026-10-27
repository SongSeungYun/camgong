package com.camgong.auth;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 필터 단계에서는 MVC 예외 핸들러가 닿지 않아 같은 모양의 ProblemDetail JSON 을 직접 쓴다. */
final class ProblemResponses {

    private ProblemResponses() {
    }

    static void write(HttpServletResponse response, JsonMapper mapper, HttpStatus status, String title, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), Map.of(
                "type", "about:blank", "title", title, "status", status.value(), "detail", detail,
                "errors", List.of()));
    }
}
