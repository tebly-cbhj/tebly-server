package com.example.teblyserver.schedule.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoryUpdateRequestDto(
        String name,
        String icon,
        // null 허용
        Boolean isPrivate
) {
}
