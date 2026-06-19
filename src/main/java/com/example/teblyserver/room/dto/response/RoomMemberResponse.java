package com.example.teblyserver.room.dto.response;

import com.example.teblyserver.room.domain.RoomMember;
import com.example.teblyserver.room.domain.RoomRole;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record RoomMemberResponse(
        @JsonProperty("user_id") Long userId,
        String nickname,
        @JsonProperty("profile_image") String profileImage,
        RoomRole role,
        LocalDateTime createdAt
) {

    public static RoomMemberResponse of(RoomMember member) {
        return new RoomMemberResponse(
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getUser().getProfileImageUrl(),
                member.getRole(),
                member.getCreatedAt()
        );
    }
}
