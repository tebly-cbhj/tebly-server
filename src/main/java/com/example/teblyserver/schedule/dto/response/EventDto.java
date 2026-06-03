package com.example.teblyserver.schedule.dto.response;

import com.example.teblyserver.schedule.domain.RepeatType;
import com.example.teblyserver.schedule.domain.Schedule;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record EventDto(
        @JsonProperty("event_id") Long eventId,
        String title,
        @JsonProperty("start_at") LocalDateTime startAt,
        @JsonProperty("end_at") LocalDateTime endAt,
        @JsonProperty("repeat_type") RepeatType repeatType
) {
    // 엔티티 객체를 DTO로 편하게 변환하기 위한 정적 팩토리 메서드
    public static EventDto from(Schedule schedule) {
        return new EventDto(
                schedule.getId(),
                schedule.getTitle(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getRepeatType()
        );
    }
}
