package com.example.messenger.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email(message = "이메일 형식이 아닙니다.")
        @NotBlank(message = "이메일을 입력하세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력하세요.")
        @Size(min = 4, max = 64, message = "비밀번호는 4~64자입니다.")
        String password,

        @NotBlank(message = "닉네임을 입력하세요.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자입니다.")
        @Pattern(regexp = "^[\\p{L}0-9_가-힣]+$", message = "닉네임은 한글/영문/숫자/언더스코어만 가능합니다.")
        String nickname
) {}
