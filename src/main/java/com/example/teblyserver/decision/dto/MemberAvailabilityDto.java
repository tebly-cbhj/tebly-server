package com.example.teblyserver.decision.dto;

import java.util.List;

/**
 * 후보가 없을 때(noCandidate=true) 대안 제안을 위한 멤버별 가능 시간대.
 * 예: freeRanges = ["09:00-11:00", "14:00-17:00"]
 */
public record MemberAvailabilityDto(
        String name,
        List<String> freeRanges
) {
}
