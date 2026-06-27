package com.example.teblyserver.decision.dto;

/**
 * 후보 시간대의 가용/불가용 멤버 요약 (이름만 노출).
 */
public record MemberSummaryDto(
        String name
) {
}
