package com.example.messenger.message.mapper;

import com.example.messenger.message.dto.ReactionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ReactionMapper {

    /** 새 반응 추가. UNIQUE 위반은 호출자가 미리 토글 로직으로 차단해야 함. */
    int insert(@Param("messageId") Long messageId,
               @Param("userId") Long userId,
               @Param("emoji") String emoji);

    /** (messageId, userId, emoji) 의 반응 제거. 영향받은 행 수 반환 — toggle 판정에 사용. */
    int delete(@Param("messageId") Long messageId,
               @Param("userId") Long userId,
               @Param("emoji") String emoji);

    /** 한 메시지의 모든 반응 (emoji, created_at 순 평면 행). */
    List<ReactionRow> findRowsByMessageId(@Param("messageId") Long messageId);

    /** 여러 메시지의 모든 반응 — 페이지 단위 조회. ids 비어 있으면 빈 리스트. */
    List<ReactionRow> findRowsByMessageIds(@Param("ids") Collection<Long> ids);
}
