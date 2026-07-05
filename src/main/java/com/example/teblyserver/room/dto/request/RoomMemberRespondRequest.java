package com.example.teblyserver.room.dto.request;

import com.example.teblyserver.room.domain.InviteStatus;
import jakarta.validation.constraints.NotNull;

public record RoomMemberRespondRequest(
        @NotNull(message = "초대 응답 상태는 필수입니다.")
        InviteStatus status // ACCEPTED 또는 REJECTED

) {
}
