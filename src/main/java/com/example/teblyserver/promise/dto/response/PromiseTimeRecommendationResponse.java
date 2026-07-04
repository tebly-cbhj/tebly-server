package com.example.teblyserver.promise.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record PromiseTimeRecommendationResponse(
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer durationMinutes,

        Integer availableMemberCount,
        Integer totalMemberCount,

        /*
            allAvailable = true
            → 멤버 전원 가능한 시간

            allAvailable = false
            → 일부 멤버와 일정 충돌이 있는 대안 시간
         */
        Boolean allAvailable,

        String reason,

        List<PromiseRecommendationMemberResponse> availableMembers,
        List<PromiseRecommendationMemberResponse> unavailableMembers
) {
}
