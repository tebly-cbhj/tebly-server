package com.example.teblyserver.decision.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.time.LocalDate;
import java.util.List;

/**
 * 추천 알고리즘 결과를 Redis에 저장할 때 쓰는 캐시 DTO.
 * 기존 LLMDecisionRequestDto에서 roomId를 제거한 구조와 동일하다.
 * - noCandidate=false: candidates 사용
 * - noCandidate=true : memberAvailability + rangeFrom/rangeTo 사용
 *
 * Redis에는 JSON 문자열로 저장된다. LocalDate는 ISO-8601 문자열로 직렬화한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DecisionCacheDto(
        boolean noCandidate,

        List<CandidateSlotDto> candidates,

        List<MemberAvailabilityDto> memberAvailability,

        @JsonSerialize(using = LocalDateSerializer.class)
        @JsonDeserialize(using = LocalDateDeserializer.class)
        LocalDate rangeFrom,

        @JsonSerialize(using = LocalDateSerializer.class)
        @JsonDeserialize(using = LocalDateDeserializer.class)
        LocalDate rangeTo
) {
}
