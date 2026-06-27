package com.example.teblyserver.decision.service;

import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.decision.dto.CandidateSlotDto;
import com.example.teblyserver.decision.dto.MemberAvailabilityDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini에 넘길 시스템/유저 프롬프트를 생성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            당신은 일정 조율 앱에서 시간 후보를 비교 설명하거나,
            적합한 시간이 없을 때 최선의 타협안을 제시하는 도우미입니다.
            결과는 방 멤버 전원이 함께 보는 화면에 노출됩니다.

            [판단 시 활용 가능한 정보]
            - 각 후보의 가용 멤버 수, 전원 가능 여부(allAvailable), 시간대를
              바탕으로 최적 시간/대안을 고르는 판단에 활용하세요.

            [출력 문장 작성 규칙]
            - 특정 멤버의 참여 이력이나 선호도를 근거로 언급하지 마세요
              (예: "최근 참여가 적으셨던", "~님 선호 시간대라서" 금지)
            - 결과 중심으로 담백하고 자연스러운 한국어로 작성하세요
            - 부담스럽지 않은 톤 유지

            [후보 있을 때 출력 형식]
            {"type": "comparison", "summaries": [{"slotId": "...", "summary": "..."}]}

            [후보 없을 때 출력 형식]
            {"type": "alternative", "proposedSlot": {"date": "...", "startTime": "...", "endTime": "...", "summary": "..."}}

            반드시 JSON만 출력하고 마크다운 코드블록 없이 순수 JSON만 반환하세요.
            """;

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * 후보가 있을 때: 가용성 기반 후보 목록을 JSON으로 직렬화해 삽입.
     */
    public String buildComparisonPrompt(List<CandidateSlotDto> candidates) {
        String candidatesJson = toJson(Map.of("candidates", candidates));

        return """
                다음은 추천된 약속 시간 후보들입니다. 각 후보의 특징을 비교해서 설명해주세요.

                %s
                """.formatted(candidatesJson);
    }

    /**
     * 후보가 없을 때: 멤버별 가능 시간대(freeRanges)와 탐색 기간을 삽입하고 대안을 요청.
     */
    public String buildAlternativePrompt(List<MemberAvailabilityDto> memberAvailability,
                                         LocalDate rangeFrom,
                                         LocalDate rangeTo) {
        Map<String, Object> dateRange = new LinkedHashMap<>();
        dateRange.put("from", rangeFrom);
        dateRange.put("to", rangeTo);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberAvailability", memberAvailability);
        payload.put("dateRange", dateRange);

        String payloadJson = toJson(payload);

        return """
                추천 가능한 시간대가 없습니다.
                다음 멤버별 가능 시간대를 보고 최선의 타협안을 제안해주세요.

                %s
                """.formatted(payloadJson);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("프롬프트 직렬화 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
    }
}
