package com.example.teblyserver.room.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RoomMemberKickRequest(
        @NotNull(message = "강퇴할 멤버 ID 목록이 필요합니다.")
        @JsonProperty("user_ids")
        List<Long> userIds
) {
}
