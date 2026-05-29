package com.example.teblyserver.schedule.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoryCreateRequestDto(
        String name,
        String icon,
        @JsonProperty("is_private") boolean isPrivate
) {
}
