package com.example.teblyserver.decision.dto;

/**
 * 후보 시간대의 가용/불가용 멤버 요약.
 * beforeSlot/afterSlot: 이 멤버의 슬롯 직전/직후 인접 일정 (없으면 null).
 * 가용(available) 멤버에 한해 채워지며, 불가용(unavailable) 멤버는 항상 null이다.
 */
public record MemberSummaryDto(
        String name,
        AdjacentScheduleDto beforeSlot,
        AdjacentScheduleDto afterSlot
) {
}
