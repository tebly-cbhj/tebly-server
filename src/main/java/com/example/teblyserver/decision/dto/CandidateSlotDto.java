package com.example.teblyserver.decision.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알고리즘(PromiseRecommendationService)이 산출한 약속 시간 후보.
 * slotId는 프론트 식별용으로 부여한다 (예: "slot-1").
 * Gemini 프롬프트용 JSON으로 직렬화되므로 LocalDateTime은 ISO-8601 문자열로 직렬화한다.
 */
public record CandidateSlotDto(
        String slotId,

        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        LocalDateTime startTime,

        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        LocalDateTime endTime,

        int durationMinutes,
        boolean allAvailable,
        int availableMemberCount,
        int totalMemberCount,
        List<MemberSummaryDto> availableMembers,
        List<MemberSummaryDto> unavailableMembers
) {
}
