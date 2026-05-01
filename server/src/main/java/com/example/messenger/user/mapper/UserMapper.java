package com.example.messenger.user.mapper;

import com.example.messenger.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper {

    Optional<User> findById(@Param("id") Long id);

    Optional<User> findByEmail(@Param("email") String email);

    int countByEmail(@Param("email") String email);

    int insert(User user);

    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("statusMessage") String statusMessage,
                      @Param("profileImageUrl") String profileImageUrl);

    /** 이메일/닉네임 부분 일치 검색 (자기 자신은 제외). */
    List<User> search(@Param("query") String query,
                      @Param("excludeUserId") Long excludeUserId,
                      @Param("limit") int limit);
}
