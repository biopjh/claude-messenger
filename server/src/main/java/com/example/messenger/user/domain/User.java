package com.example.messenger.user.domain;

import lombok.*;

import java.time.OffsetDateTime;

/**
 * users 테이블 매핑 도메인.
 * MyBatis가 application.yml의 map-underscore-to-camel-case 옵션으로
 * password_hash <-> passwordHash 등을 자동 매핑한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private Long id;
    private String email;
    private String passwordHash;
    private String nickname;
    private String profileImageUrl;
    private String statusMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
