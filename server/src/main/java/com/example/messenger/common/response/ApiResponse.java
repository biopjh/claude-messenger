package com.example.messenger.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * 모든 REST 응답을 감싸는 통일 봉투(envelope).
 * 성공 시: { success: true, data: ..., error: null, timestamp }
 * 실패 시: { success: false, data: null, error: { code, message }, timestamp }
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message), OffsetDateTime.now());
    }

    public record ApiError(String code, String message) {}
}
