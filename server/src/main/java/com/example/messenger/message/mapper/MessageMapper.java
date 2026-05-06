package com.example.messenger.message.mapper;

import com.example.messenger.message.domain.Message;
import com.example.messenger.message.dto.MessageResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface MessageMapper {

    int insert(Message message);

    /** 첨부 1건을 attachments 테이블에 저장. */
    int insertAttachment(@Param("messageId") Long messageId,
                         @Param("url") String url,
                         @Param("mimeType") String mimeType,
                         @Param("sizeBytes") Long sizeBytes);

    /** 단일 메시지 조회 (수정/삭제 권한 검증, 답장 대상 검증 등에 사용) */
    Optional<Message> findById(@Param("id") Long id);

    /** 단일 메시지의 화면용 DTO (수정/삭제 후 브로드캐스트용) */
    Optional<MessageResponse> findResponseById(@Param("id") Long id);

    /** 본문 수정 (TEXT 만 허용, 5분 이내 정책은 서비스 레이어에서 검증) */
    int updateContent(@Param("id") Long id,
                      @Param("content") String content,
                      @Param("editedAt") OffsetDateTime editedAt);

    /** 소프트 삭제 — deleted_at 만 채운다. content 는 그대로(인용 추적 등을 위해). */
    int softDelete(@Param("id") Long id,
                   @Param("deletedAt") OffsetDateTime deletedAt);

    /**
     * 한 방의 메시지를 cursor 기반으로 페이지네이션. attachments + reply 메시지 LEFT JOIN.
     */
    List<MessageResponse> findPage(@Param("roomId") Long roomId,
                                   @Param("cursorId") Long cursorId,
                                   @Param("size") int size);

    /**
     * 한 방 안에서 본문 부분일치 검색. ILIKE + pg_trgm GIN 인덱스로 빠름.
     * 삭제된 메시지 / SYSTEM 메시지는 결과에서 제외.
     */
    List<MessageResponse> searchInRoom(@Param("roomId") Long roomId,
                                       @Param("query") String query,
                                       @Param("size") int size);
}
