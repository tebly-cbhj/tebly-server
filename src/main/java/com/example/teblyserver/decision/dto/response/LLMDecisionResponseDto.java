package com.example.teblyserver.decision.dto.response;

import com.example.teblyserver.decision.dto.ProposedSlotDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 결정 도우미 응답.
 * - type="recommendation": recommendedSlotId/reason(/alternativeNote) 채워짐 (단일 최적 추천)
 * - type="alternative": proposedSlot 채워짐 (대안 제시)
 * fallbackUsed는 SanitizeService 후처리로 문구가 대체된 경우 "true".
 *
 * Gemini 응답 JSON을 그대로 역직렬화하므로, 모르는 필드는 무시하고
 * null 필드는 직렬화에서 제외한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LLMDecisionResponseDto(
        String type,
        String recommendedSlotId,
        String reason,
        String alternativeNote,
        ProposedSlotDto proposedSlot,
        String fallbackUsed
) {
    public static LLMDecisionResponseDto recommendation(String recommendedSlotId, String reason,
                                                         String alternativeNote, String fallbackUsed) {
        return new LLMDecisionResponseDto("recommendation", recommendedSlotId, reason, alternativeNote, null, fallbackUsed);
    }

    public static LLMDecisionResponseDto alternative(ProposedSlotDto proposedSlot, String fallbackUsed) {
        return new LLMDecisionResponseDto("alternative", null, null, null, proposedSlot, fallbackUsed);
    }
}
