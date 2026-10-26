package com.camgong.common;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import tools.jackson.core.JacksonException;

/** 모든 오류를 ProblemDetail + errors[{field, message}] 로 돌려준다. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String INVALID_INPUT = "입력값이 올바르지 않습니다.";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException e) {
        return body(e.getStatus(), e.getTitle(), e.getMessage(), e.getErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
        List<ApiException.Field> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiException.Field(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return body(HttpStatus.BAD_REQUEST, "Bad Request", INVALID_INPUT, errors);
    }

    /** 열거형 값 변환 실패, 날짜 형식 오류 등. 원인 예외의 경로에서 필드명을 꺼낸다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException e) {
        String field = null;
        if (e.getCause() instanceof JacksonException je && !je.getPath().isEmpty()) {
            field = je.getPath().get(je.getPath().size() - 1).getPropertyName();
        }
        List<ApiException.Field> errors = field == null
                ? List.of()
                : List.of(new ApiException.Field(field, "값의 형식이 올바르지 않습니다."));
        return body(HttpStatus.BAD_REQUEST, "Bad Request", INVALID_INPUT, errors);
    }

    private static ResponseEntity<ProblemDetail> body(HttpStatus status, String title, String detail,
            List<ApiException.Field> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("errors", errors);
        return ResponseEntity.status(status).body(pd);
    }
}
