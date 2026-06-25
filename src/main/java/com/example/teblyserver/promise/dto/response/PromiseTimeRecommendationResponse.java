package com.example.teblyserver.promise.dto.response;

import java.time.LocalDateTime;

public record PromiseTimeRecommendationResponse(
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer durationMinutes,

        Integer availableMemberCount,
        Integer totalMemberCount,

        String reason
) {
}
