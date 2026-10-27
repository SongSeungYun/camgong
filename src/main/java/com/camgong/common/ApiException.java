package com.camgong.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** 상태 코드, title(409는 DUPLICATE_REQUEST 같은 코드), 필드별 오류 목록을 가진 API 오류. */
public class ApiException extends RuntimeException {

    public record Field(String field, String message) {
    }

    private final HttpStatus status;
    private final String title;
    private final List<Field> errors;
    private final String reason;
    private final Map<String, Object> properties = new LinkedHashMap<>();
    private OffsetDateTime retryAt;
    private Long retryAfterSeconds;

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

    /** 한도 오류용: 본문 속성 retryAt 과 Retry-After(초, 올림) 헤더를 함께 싣는다. */
    public ApiException retryAt(Instant retryAt, Instant now) {
        // ponytail: 응답 오프셋은 KST 고정(001·002 응답과 같다).
        this.retryAt = retryAt.atOffset(ZoneOffset.ofHours(9));
        long millis = Math.max(0, retryAt.toEpochMilli() - now.toEpochMilli());
        this.retryAfterSeconds = (millis + 999) / 1000;
        return this;
    }

    /** 본문에 더 싣는 속성(예: PRICE_CHANGED 의 currentUnitPrice). */
    public ApiException property(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public OffsetDateTime getRetryAt() {
        return retryAt;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
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
