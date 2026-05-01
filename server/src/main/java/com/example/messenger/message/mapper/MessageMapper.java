package com.example.messenger.message.mapper;

import com.example.messenger.message.domain.Message;
import com.example.messenger.message.dto.MessageResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {

    int insert(Message message);

    /** 첨부 1건을 attachments 테이블에 저장. */
    int insertAttachment(@Param("messageId") Long messageId,
                         @Param("url") String url,
                         @Param("mimeType") String mimeType,
                         @Param("sizeBytes") Long sizeBytes);

    /**
     * 한 방의 메시지를 cursor 기반으로 페이지네이션. attachments 1:0..1 LEFT JOIN.
     */
    List<MessageResponse> findPage(@Param("roomId") Long roomId,
                                   @Param("cursorId") Long cursorId,
                                   @Param("size") int size);
}
