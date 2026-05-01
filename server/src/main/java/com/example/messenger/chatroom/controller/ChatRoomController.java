package com.example.messenger.chatroom.controller;

import com.example.messenger.auth.jwt.JwtAuthFilter.AuthPrincipal;
import com.example.messenger.chatroom.dto.ChatRoomDetail;
import com.example.messenger.chatroom.dto.ChatRoomSummary;
import com.example.messenger.chatroom.dto.CreateGroupRequest;
import com.example.messenger.chatroom.dto.InviteMembersRequest;
import com.example.messenger.chatroom.service.ChatRoomService;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.common.response.ApiResponse;
import com.example.messenger.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    /** 1:1 방 가져오기 또는 생성. */
    @PostMapping("/direct/{userId}")
    public ApiResponse<Map<String, Object>> getOrCreateDirect(@AuthenticationPrincipal AuthPrincipal me,
                                                              @PathVariable Long userId) {
        requireMe(me);
        Long roomId = chatRoomService.getOrCreateDirectRoom(me.userId(), userId);
        return ApiResponse.ok(Map.of("roomId", roomId));
    }

    /** 그룹채팅 만들기. */
    @PostMapping("/group")
    public ApiResponse<Map<String, Object>> createGroup(@AuthenticationPrincipal AuthPrincipal me,
                                                        @Valid @RequestBody CreateGroupRequest req) {
        requireMe(me);
        Long roomId = chatRoomService.createGroupRoom(me.userId(), req.title(), req.memberIds());
        return ApiResponse.ok(Map.of("roomId", roomId));
    }

    /** 그룹채팅 멤버 초대. */
    @PostMapping("/{roomId}/invite")
    public ApiResponse<List<UserResponse>> invite(@AuthenticationPrincipal AuthPrincipal me,
                                                  @PathVariable Long roomId,
                                                  @Valid @RequestBody InviteMembersRequest req) {
        requireMe(me);
        var added = chatRoomService.invite(roomId, me.userId(), req.userIds())
                .stream().map(UserResponse::from).toList();
        return ApiResponse.ok(added);
    }

    /** 그룹채팅 나가기. */
    @PostMapping("/{roomId}/leave")
    public ApiResponse<Void> leave(@AuthenticationPrincipal AuthPrincipal me,
                                   @PathVariable Long roomId) {
        requireMe(me);
        chatRoomService.leave(roomId, me.userId());
        return ApiResponse.ok();
    }

    /** 내 채팅방 목록 + 마지막 메시지 + 안읽음 수. */
    @GetMapping
    public ApiResponse<List<ChatRoomSummary>> myRooms(@AuthenticationPrincipal AuthPrincipal me) {
        requireMe(me);
        return ApiResponse.ok(chatRoomService.listMyRooms(me.userId()));
    }

    /** 방 상세 (멤버 목록 포함). */
    @GetMapping("/{roomId}")
    public ApiResponse<ChatRoomDetail> detail(@AuthenticationPrincipal AuthPrincipal me,
                                              @PathVariable Long roomId) {
        requireMe(me);
        return ApiResponse.ok(chatRoomService.getDetail(me.userId(), roomId));
    }

    private static void requireMe(AuthPrincipal me) {
        if (me == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
}
