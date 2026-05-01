package com.example.messenger.common.exception;

import com.example.messenger.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 우리가 명시적으로 던지는 비즈니스 예외 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException e) {
        ErrorCode ec = e.getErrorCode();
        log.warn("ApiException: {} - {}", ec.code(), e.getMessage());
        return ResponseEntity
                .status(ec.status())
                .body(ApiResponse.fail(ec.code(), e.getMessage()));
    }

    /** @Valid 검증 실패 (DTO 필드 검증) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(ErrorCode.BAD_REQUEST.status())
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST.code(), detail));
    }

    /** 그 외 예측 못한 예외 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.code(),
                        ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    private String formatFieldError(FieldError fe) {
        return "%s: %s".formatted(fe.getField(), fe.getDefaultMessage());
    }
}
