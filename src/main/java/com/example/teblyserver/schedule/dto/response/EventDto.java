package com.example.teblyserver.schedule.dto.response;

import com.example.teblyserver.schedule.domain.Category;
import com.example.teblyserver.schedule.domain.RepeatType;
import com.example.teblyserver.schedule.domain.Schedule;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record EventDto(
        @JsonProperty("event_id") Long eventId,

        @JsonProperty("category")
        CategoryResponseDto category,

        String title,
        @JsonProperty("start_at") LocalDateTime startAt,
        @JsonProperty("end_at") LocalDateTime endAt,
        @JsonProperty("repeat_type") RepeatType repeatType
) {
    // 엔티티 객체를 DTO로 편하게 변환하기 위한 정적 팩토리 메서드
    public static EventDto from(Schedule schedule, Long loginUserId) {
        Category categoryEntity = schedule.getCategory();

        // [핵심 조건]
        // 1. 일정을 조회하는 사람(loginUserId)이 일정 주인(schedule.getUser().getId())이 아니고, (친구가 조회)
        // 2. 해당 카테고리가 비공개(isPrivate == true) 상태일 때만 true
        boolean isMasked = !schedule.getUser().getId().equals(loginUserId)
                && categoryEntity.isPrivate();

        // 마스킹 상태라면 제목도 "일정"으로 덮어씌움
        String displayTitle = isMasked ? "일정" : schedule.getTitle();

        return new EventDto(
                schedule.getId(),
                // 가려야 하는지 여부(isMasked)를 넘겨주면 CategoryResponseDto가 알아서 판단해서 변환
                CategoryResponseDto.of(categoryEntity, isMasked),
                displayTitle,
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getRepeatType()
        );
    }
}
