package com.example.messenger.chatroom.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 그룹채팅 생성 요청.
 *  - title: 1~80자
 *  - memberIds: 초대할 사용자 id 목록(자기 자신 제외, 최소 1명). 모두 친구여야 한다.
 */
public record CreateGroupRequest(
        @NotNull @Size(min = 1, max = 80, message = "방 제목은 1~80자입니다.")
        String title,

        @NotEmpty(message = "초대할 멤버를 1명 이상 선택하세요.")
        List<Long> memberIds
) {}
