package com.example.teblyserver.schedule.dto.request;

import com.example.teblyserver.schedule.domain.RepeatType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ScheduleRequestDto(
        String title,
        @JsonProperty("start_at")
        LocalDateTime startTime,
        @JsonProperty("end_at")
        LocalDateTime endTime,
        @JsonProperty("repeat_type")
        RepeatType repeatType
) {}
