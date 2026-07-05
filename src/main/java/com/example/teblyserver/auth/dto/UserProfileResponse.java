package com.example.teblyserver.auth.dto;

import com.example.teblyserver.auth.domain.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class UserProfileResponse {
    private Long id;
    private String email;
    private String nickname;
    private String bio;
    private String profileImageUrl;

    @Getter(onMethod_ = @__(@JsonProperty("isNewUser")))
    private boolean isNewUser;

    public UserProfileResponse(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.nickname = user.getNickname();
        this.bio = user.getBio();
        this.profileImageUrl = user.getProfileImageUrl();
        this.isNewUser = user.isNewUser();
    }
}