package com.example.teblyserver.room.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomUpdateRequest(
        @NotBlank(message = "방 이름은 필수입니다.")
        @Size(max = 50, message = "방 이름은 50자를 넘을 수 없습니다.")
        String name,

        @Size(max = 100, message = "한 줄 소개는 100자를 넘을 수 없습니다.")
        String description,

        String imageUrl
) {
}
