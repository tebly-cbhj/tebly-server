package com.example.teblyserver.schedule.dto.response;

import java.util.List;

public record ScheduleResponseDto(
        List<EventDto> events
) {
    public static ScheduleResponseDto from(List<EventDto> events) {
        return new ScheduleResponseDto(events);
    }
}
