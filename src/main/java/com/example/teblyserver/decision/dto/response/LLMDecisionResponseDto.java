package com.example.teblyserver.decision.dto.response;

import com.example.teblyserver.decision.dto.ProposedSlotDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 결정 도우미 응답.
 * - type="recommendation": recommendedSlotId/reason(/alternativeNote) 채워짐 (단일 최적 추천)
 *   finalStartTime/finalEndTime은 선택된 후보(slot) 구간 안에서 LLM이 실제로 제안하는 시간(HH:mm)이다.
 *   후보 구간이 최소 약속 시간보다 훨씬 길면(최소 시간+2시간 초과) LLM이 그 안에서 적당한 구간으로 좁혀 담는다.
 * - type="alternative": proposedSlot 채워짐 (대안 제시)
 * fallbackUsed는 SanitizeService 후처리로 문구가 대체된 경우 "true".
 * promiseId는 type="recommendation"이라 실제 약속이 생성된 경우에만 채워지고,
 * type="alternative"(후보 없음)는 검증되지 않은 제안이라 약속을 생성하지 않으므로 항상 null이다.
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
        String finalStartTime,
        String finalEndTime,
        ProposedSlotDto proposedSlot,
        String fallbackUsed,
        Long promiseId
) {
    public static LLMDecisionResponseDto recommendation(String recommendedSlotId, String reason,
                                                         String alternativeNote, String finalStartTime,
                                                         String finalEndTime, String fallbackUsed) {
        return new LLMDecisionResponseDto("recommendation", recommendedSlotId, reason, alternativeNote,
                finalStartTime, finalEndTime, null, fallbackUsed, null);
    }

    public static LLMDecisionResponseDto alternative(ProposedSlotDto proposedSlot, String fallbackUsed) {
        return new LLMDecisionResponseDto("alternative", null, null, null, null, null, proposedSlot, fallbackUsed, null);
    }

    public LLMDecisionResponseDto withPromiseId(Long promiseId) {
        return new LLMDecisionResponseDto(type, recommendedSlotId, reason, alternativeNote,
                finalStartTime, finalEndTime, proposedSlot, fallbackUsed, promiseId);
    }

    public LLMDecisionResponseDto withFinalTimeRange(String finalStartTime, String finalEndTime) {
        return new LLMDecisionResponseDto(type, recommendedSlotId, reason, alternativeNote,
                finalStartTime, finalEndTime, proposedSlot, fallbackUsed, promiseId);
    }
}
