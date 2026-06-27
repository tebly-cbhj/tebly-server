package com.example.teblyserver.promise.dto.internal;

import com.example.teblyserver.schedule.domain.RepeatType;

import java.time.LocalDateTime;

/*
 * schedule 엔티티 전체를 가져오지 않고, 필요한 값만 담기 위해 만든 DTO
 * 누구의 일정인가?
 * 언제 시작하는가?
 * 언제 끝나는가?
 */
public record BusyScheduleTimeRange(
        Long userId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        RepeatType repeatType
) {
}
