package com.camgong.common;

import java.util.List;

import org.springframework.http.HttpStatus;

/** 상태 코드, title(409는 DUPLICATE_REQUEST 같은 코드), 필드별 오류 목록을 가진 API 오류. */
public class ApiException extends RuntimeException {

    public record Field(String field, String message) {
    }

    private final HttpStatus status;
    private final String title;
    private final List<Field> errors;

    public ApiException(HttpStatus status, String title, String detail, List<Field> errors) {
        super(detail);
        this.status = status;
        this.title = title;
        this.errors = errors;
    }

    public ApiException(HttpStatus status, String title, String detail) {
        this(status, title, detail, List.of());
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public List<Field> getErrors() {
        return errors;
    }
}
