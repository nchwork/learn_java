package com.xhunting.coreapi.common;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Một chỗ duy nhất đổi exception thành response lỗi dạng grpc-gateway
 * {@code {"code": 5, "message": "CaseNotFound", "details": []}}.
 *
 * Thay cho khối {@code try/catch} trong {@code CoreApiServer.dispatch} của bản core,
 * và middleware xử lý lỗi / {@code runtime.HTTPError} của grpc-gateway trong Go.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return error(e.code().httpStatus, e.code().grpcCode, e.getMessage());
    }

    /** Lỗi từ {@code @Valid}: gom message của mọi field, giống bản core ("a; b; c"). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(400, ApiException.Code.INVALID_ARGUMENT.grpcCode, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> handleBadJson(HttpMessageNotReadableException e) {
        return error(400, ApiException.Code.INVALID_ARGUMENT.grpcCode, "invalid JSON body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(400, ApiException.Code.INVALID_ARGUMENT.grpcCode, "invalid parameter: " + e.getName());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException e) {
        return error(404, 5, "Not Found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return error(405, 12, "Method Not Allowed");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("unhandled error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR.value(), ApiException.Code.INTERNAL.grpcCode, "internal error");
    }

    private static ResponseEntity<Map<String, Object>> error(int httpStatus, int grpcCode, String message) {
        return ResponseEntity.status(httpStatus)
                .body(Map.of("code", grpcCode, "message", message, "details", List.of()));
    }
}
