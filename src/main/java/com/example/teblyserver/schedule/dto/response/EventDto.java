package com.example.teblyserver.schedule.dto.response;

import com.example.teblyserver.schedule.domain.Category;
import com.example.teblyserver.schedule.domain.RepeatType;
import com.example.teblyserver.schedule.domain.Schedule;
import com.example.teblyserver.schedule.domain.ScheduleReminder;

import java.time.LocalDateTime;
import java.util.List;

public record EventDto(
        Long eventId,

        CategoryResponseDto category,

        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        RepeatType repeatType,
        List<Integer> notificationLeadMinutes,
        String location,
        String memo,
        LocalDateTime repeatUntil
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
        // [추가] 마스킹 상태라면 장소와 메모도 숨김 처리
        String displayLocation = isMasked ? null : schedule.getLocation();
        String displayMemo = isMasked ? null : schedule.getMemo();

        return new EventDto(
                schedule.getId(),
                // 가려야 하는지 여부(isMasked)를 넘겨주면 CategoryResponseDto가 알아서 판단해서 변환
                CategoryResponseDto.of(categoryEntity, isMasked),
                displayTitle,
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getRepeatType(),
                extractLeadMinutes(schedule),
                displayLocation,
                displayMemo,
                schedule.getRepeatUntil()
        );
    }

    // 반복 일정을 캘린더에 뿌려주기 위해 시간만 갈아끼우는 전용 팩토리 메서드
    public static EventDto fromExpanded(Schedule schedule, Long loginUserId, LocalDateTime expandedStart, LocalDateTime expandedEnd) {
        Category categoryEntity = schedule.getCategory();

        boolean isMasked = !schedule.getUser().getId().equals(loginUserId) && categoryEntity.isPrivate();
        String displayTitle = isMasked ? "일정" : schedule.getTitle();
        String displayLocation = isMasked ? null : schedule.getLocation();
        String displayMemo = isMasked ? null : schedule.getMemo();

        return new EventDto(
                schedule.getId(),
                CategoryResponseDto.of(categoryEntity, isMasked),
                displayTitle,
                expandedStart, // 새롭게 계산된 반복 시간
                expandedEnd,   // 새롭게 계산된 반복 종료 시간
                schedule.getRepeatType(),
                extractLeadMinutes(schedule),
                displayLocation,
                displayMemo,
                schedule.getRepeatUntil()
        );
    }

    // Schedule의 reminders 리스트에서 leadMinutes 값들만 뽑아내는 헬퍼
    private static List<Integer> extractLeadMinutes(Schedule schedule) {
        return schedule.getReminders().stream()
                .map(ScheduleReminder::getLeadMinutes)
                .toList();
    }
}
