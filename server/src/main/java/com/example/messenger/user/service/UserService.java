package com.example.messenger.user.service;

import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.user.domain.User;
import com.example.messenger.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userMapper.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userMapper.findByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userMapper.countByEmail(email) > 0;
    }

    @Transactional
    public Long create(String email, String passwordHash, String nickname) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .nickname(nickname)
                .build();
        userMapper.insert(user);
        return user.getId(); // useGeneratedKeys 로 채워진 PK
    }

    @Transactional
    public void updateProfile(Long id, String nickname, String statusMessage, String profileImageUrl) {
        userMapper.updateProfile(id, nickname, statusMessage, profileImageUrl);
    }

    @Transactional(readOnly = true)
    public java.util.List<User> search(String query, Long excludeUserId, int limit) {
        if (query == null || query.isBlank()) return java.util.List.of();
        return userMapper.search(query.trim(), excludeUserId, Math.max(1, Math.min(limit, 50)));
    }
}
