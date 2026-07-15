package com.example.teblyserver.promise.dto.internal;

import com.example.teblyserver.schedule.domain.RepeatType;

import java.time.LocalDateTime;

/*
 * schedule 엔티티 전체를 가져오지 않고, 필요한 값만 담기 위해 만든 DTO
 * 누구의 일정인가?
 * 언제 시작하는가?
 * 언제 끝나는가?
 * (title은 결정 도우미가 후보 슬롯 앞뒤 인접 일정을 사람이 알아볼 수 있게 설명할 때 사용)
 * (categoryName은 약속 초대 시 충돌 일정의 중요도를 판단할 때 사용)
 */
public record BusyScheduleTimeRange(
        Long scheduleId,
        Long userId,
        String title,
        String categoryName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        RepeatType repeatType
) {
}
