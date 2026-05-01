package com.example.messenger.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHENTICATED     (HttpStatus.UNAUTHORIZED, "AUTH_002", "로그인이 필요합니다."),
    INVALID_TOKEN       (HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN       (HttpStatus.UNAUTHORIZED, "AUTH_004", "토큰이 만료되었습니다."),

    // User
    EMAIL_ALREADY_USED  (HttpStatus.CONFLICT,     "USER_001", "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND      (HttpStatus.NOT_FOUND,    "USER_002", "사용자를 찾을 수 없습니다."),

    // Validation / common
    BAD_REQUEST         (HttpStatus.BAD_REQUEST,  "COMMON_001", "잘못된 요청입니다."),
    INTERNAL_ERROR      (HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status()         { return status; }
    public String code()               { return code; }
    public String defaultMessage()     { return defaultMessage; }
}
