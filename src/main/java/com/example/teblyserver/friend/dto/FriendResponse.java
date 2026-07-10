package com.example.teblyserver.friend.dto;

import com.example.teblyserver.auth.domain.User;
import lombok.Getter;

@Getter
public class FriendResponse {
    private Long id;
    private String nickname;
    private String bio;
    private String profileImageUrl;

    public FriendResponse(User user) {
        this.id = user.getId();
        this.nickname = user.getNickname();
        this.bio = user.getBio();
        this.profileImageUrl = user.getProfileImageUrl();
    }
}