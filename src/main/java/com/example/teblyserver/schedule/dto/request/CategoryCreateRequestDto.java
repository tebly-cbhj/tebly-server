package com.example.teblyserver.schedule.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryCreateRequestDto(
        @NotBlank(message = "카테고리 이름은 비어있을 수 없습니다.")
        String name,

        @NotNull(message = "아이콘은 필수입니다.")
        String icon,

        @JsonProperty("is_private")
        boolean isPrivate
) {
}
