package com.example.teblyserver.decision.dto;

/**
 * 적합한 후보가 없을 때 제안하는 대안 시간대.
 */
public record ProposedSlotDto(
        String date,
        String startTime,
        String endTime,
        String summary
) {
}
