package com.example.teblyserver.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserProfileRequest {

    @Size(min = 1, max = 10, message = "닉네임은 1자 이상 10자 이하로 입력해주세요.")
    private String nickname;

    @Size(max = 150, message = "자기소개는 150자 이하로 입력해주세요.")
    private String bio;

    private String profileImageUrl;
}
