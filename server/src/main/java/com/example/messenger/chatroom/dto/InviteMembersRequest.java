package com.example.messenger.chatroom.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InviteMembersRequest(
        @NotEmpty(message = "초대할 사용자를 1명 이상 선택하세요.")
        List<Long> userIds
) {}
