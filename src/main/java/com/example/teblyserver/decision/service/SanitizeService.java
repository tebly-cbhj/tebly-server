package com.example.teblyserver.decision.service;

import com.example.teblyserver.decision.dto.ProposedSlotDto;
import com.example.teblyserver.decision.dto.response.LLMDecisionResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LLM이 생성한 문장에 민감 키워드(참여 이력/선호도 언급)나 특정 멤버 실명이 포함되면
 * 무난한 fallback 문구로 대체한다.
 */
@Slf4j
@Service
public class SanitizeService {

    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "참여가 적", "참여율", "못 나오셨", "오랜만에", "불참", "선호 시간"
    );

    private static final String REASON_FALLBACK = "일정과 조건을 종합적으로 고려한 시간대예요.";
    private static final String ALTERNATIVE_FALLBACK = "조율 가능한 최선의 시간대를 제안해요.";

    /**
     * 민감 키워드나 memberNames에 포함된 실명이 하나라도 발견되면 fallback 처리한 응답을 반환한다.
     * (인접 일정처럼 멤버 개인의 일정 정보가 실명과 함께 노출되는 것을 막기 위함)
     * 그렇지 않으면 원본을 그대로 반환한다.
     */
    public LLMDecisionResponseDto sanitize(LLMDecisionResponseDto response, List<String> memberNames) {
        if (response.proposedSlot() != null) {
            return sanitizeAlternative(response, memberNames);
        }

        return sanitizeRecommendation(response, memberNames);
    }

    private LLMDecisionResponseDto sanitizeRecommendation(LLMDecisionResponseDto response, List<String> memberNames) {
        boolean reasonHit = hasSensitiveContent(response.reason(), memberNames);
        boolean alternativeNoteHit = hasSensitiveContent(response.alternativeNote(), memberNames);

        if (!reasonHit && !alternativeNoteHit) {
            return response;
        }

        log.warn("결정 도우미 응답(recommendation)에서 민감 내용 감지 → fallback 문구로 대체합니다.");

        // reason이 걸리면 무난한 문구로 대체하고, alternativeNote는 걸리면 null로 대체(없어도 무방한 필드)
        String reason = reasonHit ? REASON_FALLBACK : response.reason();
        String alternativeNote = alternativeNoteHit ? null : response.alternativeNote();

        return LLMDecisionResponseDto.recommendation(response.recommendedSlotId(), reason, alternativeNote, "true");
    }

    private LLMDecisionResponseDto sanitizeAlternative(LLMDecisionResponseDto response, List<String> memberNames) {
        ProposedSlotDto original = response.proposedSlot();

        if (!hasSensitiveContent(original.summary(), memberNames)) {
            return response;
        }

        log.warn("결정 도우미 응답(alternative)에서 민감 내용 감지 → fallback 문구로 대체합니다.");

        ProposedSlotDto fallbackSlot = new ProposedSlotDto(
                original.date(),
                original.startTime(),
                original.endTime(),
                ALTERNATIVE_FALLBACK
        );

        return LLMDecisionResponseDto.alternative(fallbackSlot, "true");
    }

    private boolean hasSensitiveContent(String text, List<String> memberNames) {
        if (text == null) {
            return false;
        }

        if (SENSITIVE_KEYWORDS.stream().anyMatch(text::contains)) {
            return true;
        }

        return memberNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .anyMatch(text::contains);
    }
}
