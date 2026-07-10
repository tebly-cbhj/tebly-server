package com.example.teblyserver.schedule.dto.request;

import com.example.teblyserver.schedule.domain.RepeatType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ScheduleRequestDto(
        @NotNull(message = "카테고리 ID는 필수입니다.")
        Long categoryId,

        @NotBlank(message = "일정 제목은 비어있을 수 없습니다.")
        String title,

        @NotNull(message = "시작 시간은 필수입니다.")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startTime,

        @NotNull(message = "종료 시간은 필수입니다.")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime endTime,

        @NotNull(message = "반복 타입은 필수입니다.")
        RepeatType repeatType,

        Integer notificationLeadMinutes,

        String location,

        String memo,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime repeatUntil
) {}
