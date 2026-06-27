package com.example.teblyserver.room.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RoomMemberInviteRequest(
        @NotNull(message = "초대할 멤버 ID 목록이 필요합니다. (없으면 빈 배열 [])")
        @JsonProperty("user_ids")
        List<Long> userIds
) {
}
