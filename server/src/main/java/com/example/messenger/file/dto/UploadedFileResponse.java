package com.example.messenger.file.dto;

/**
 * 업로드 결과. 클라이언트는 이 정보를 STOMP /app/chat.send 에 그대로 실어 보낸다.
 *  - kind 는 "IMAGE" 또는 "FILE" — 클라가 메시지 type 을 결정할 때 참고
 */
public record UploadedFileResponse(
        String url,
        String originalFileName,
        String mimeType,
        long sizeBytes,
        String kind
) {}
