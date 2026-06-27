package com.example.teblyserver.promise.dto.response;

public record PromiseRecommendationMemberResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {
}
