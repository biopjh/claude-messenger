package com.example.messenger.common.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 예외. ErrorCode를 동봉해서 던지면
 * GlobalExceptionHandler가 알아서 ApiResponse로 변환합니다.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }
}
