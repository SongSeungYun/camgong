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
    private final String reason;

    public ApiException(HttpStatus status, String title, String detail, List<Field> errors) {
        this(status, title, detail, errors, null);
    }

    /** reason: 같은 title 안에서 사유를 가르는 값(예: NOT_MODIFIABLE 의 COMPLETED / DEADLINE_PASSED). */
    public ApiException(HttpStatus status, String title, String detail, List<Field> errors, String reason) {
        super(detail);
        this.status = status;
        this.title = title;
        this.errors = errors;
        this.reason = reason;
    }

    public static ApiException withReason(HttpStatus status, String title, String detail, String reason) {
        return new ApiException(status, title, detail, List.of(), reason);
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

    public String getReason() {
        return reason;
    }
}
