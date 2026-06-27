package com.example.teblyserver.decision.dto;

/**
 * 후보 시간대별 요약 설명.
 */
public record SlotSummaryDto(
        String slotId,
        String summary
) {
}
