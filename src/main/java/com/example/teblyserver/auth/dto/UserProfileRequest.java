package com.example.teblyserver.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserProfileRequest {
    private String nickname;
    private String profileImageUrl;
}