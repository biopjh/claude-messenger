package com.example.messenger.message.dto;

import com.example.messenger.message.domain.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * STOMP /app/chat.send 요청 페이로드.
 *  - type=TEXT 면 content 만 사용, 첨부 필드는 null
 *  - type=IMAGE|FILE 이면 미리 업로드해 받은 url/mime/size 를 첨부 필드로 같이 보낸다.
 *    이때 content 는 화면 표시용 원본 파일명을 권장.
 */
public record SendMessageRequest(
        @NotNull Long roomId,
        @NotNull MessageType type,
        @NotBlank @Size(max = 4000) String content,
        String attachmentUrl,
        String attachmentMimeType,
        Long attachmentSizeBytes
) {}
