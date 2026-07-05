package com.example.teblyserver.schedule.dto.request;

import com.example.teblyserver.schedule.domain.RepeatType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ScheduleUpdateRequestDto(
        Long categoryId,
        String title,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime endTime,
        RepeatType repeatType,

        Integer notificationLeadMinutes
) {}
