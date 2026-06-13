package com.example.teblyserver.room.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoomCreateRequest(
        @NotBlank(message = "방 이름은 필수입니다.")
        @Size(max = 50, message = "방 이름은 50자를 넘을 수 없습니다.")
        String name,

        @Size(max = 100, message = "한 줄 소개는 100자를 넘을 수 없습니다.")
        String description,

        // 이미지 URL은 필수가 아닐 수 있으므로 NotBlank 제외
        String imageUrl,

        @NotNull(message = "초대할 멤버 ID 목록이 필요합니다. (없으면 빈 배열 [])")
        List<Long> memberIds
) {
}
