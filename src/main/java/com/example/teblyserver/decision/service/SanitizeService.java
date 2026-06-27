package com.example.teblyserver.decision.service;

import com.example.teblyserver.decision.dto.ProposedSlotDto;
import com.example.teblyserver.decision.dto.SlotSummaryDto;
import com.example.teblyserver.decision.dto.response.LLMDecisionResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LLM이 생성한 문장에 민감 키워드(참여 이력/선호도 언급)가 포함되면
 * 무난한 fallback 문구로 대체한다.
 */
@Slf4j
@Service
public class SanitizeService {

    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "참여가 적", "참여율", "못 나오셨", "오랜만에", "불참", "선호 시간"
    );

    private static final String COMPARISON_FALLBACK = "일정과 조건을 종합적으로 고려한 시간대예요.";
    private static final String ALTERNATIVE_FALLBACK = "조율 가능한 최선의 시간대를 제안해요.";

    /**
     * 민감 키워드가 하나라도 발견되면 fallback 처리한 응답을 반환한다.
     * 그렇지 않으면 원본을 그대로 반환한다.
     */
    public LLMDecisionResponseDto sanitize(LLMDecisionResponseDto response) {
        if (!containsSensitiveKeyword(response)) {
            return response;
        }

        log.warn("결정 도우미 응답에서 민감 키워드 감지 → fallback 문구로 대체합니다.");

        if (response.summaries() != null) {
            List<SlotSummaryDto> fallbackSummaries = response.summaries().stream()
                    .map(s -> new SlotSummaryDto(s.slotId(), COMPARISON_FALLBACK))
                    .toList();
            return LLMDecisionResponseDto.comparison(fallbackSummaries, "true");
        }

        if (response.proposedSlot() != null) {
            ProposedSlotDto original = response.proposedSlot();
            ProposedSlotDto fallbackSlot = new ProposedSlotDto(
                    original.date(),
                    original.startTime(),
                    original.endTime(),
                    ALTERNATIVE_FALLBACK
            );
            return LLMDecisionResponseDto.alternative(fallbackSlot, "true");
        }

        return response;
    }

    private boolean containsSensitiveKeyword(LLMDecisionResponseDto response) {
        if (response.summaries() != null) {
            boolean hit = response.summaries().stream()
                    .map(SlotSummaryDto::summary)
                    .anyMatch(this::hasKeyword);
            if (hit) {
                return true;
            }
        }

        if (response.proposedSlot() != null) {
            return hasKeyword(response.proposedSlot().summary());
        }

        return false;
    }

    private boolean hasKeyword(String text) {
        if (text == null) {
            return false;
        }
        return SENSITIVE_KEYWORDS.stream().anyMatch(text::contains);
    }
}
