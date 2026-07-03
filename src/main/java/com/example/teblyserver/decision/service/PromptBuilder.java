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
            당신은 일정 조율 앱에서 가장 적합한 시간 후보 하나를 추천하거나,
            적합한 시간이 없을 때 최선의 타협안을 제시하는 도우미입니다.
            결과는 방 멤버 전원이 함께 보는 화면에 노출됩니다.

            [후보 있을 때: 단일 추천 규칙]
            주어진 후보들 중 가장 적합한 시간대 하나를 선택하세요.
            최적 시간대를 찾는 계산(가용 인원 집계 등)은 이미 알고리즘이 끝냈습니다.
            당신의 역할은 알고리즘이 계산하지 못하는 부분, 특히 아래 2번(인접 일정)을 사람처럼 판단하는 것입니다.

            선택 기준 우선순위:
            1. 가용 인원이 가장 많은 후보 (전원 가능 우선) — 이 조건은 항상 최우선으로 지킵니다
            2. 인접 일정이 촉박한 인원이 적은 후보를 우선합니다
               - 각 후보의 availableMembers에는 beforeSlot(직전 일정)/afterSlot(직후 일정) 정보가 있을 수 있습니다
               - beforeSlot이 있고 그 일정 종료 시각과 약속 시작 시각의 간격(gapMinutes)이 짧으면(예: 30분 이하),
                 그 멤버는 직전 일정을 마치고 촉박하게 이동해야 하므로 참석이 부담스러울 수 있습니다
               - afterSlot이 있고 약속 종료 시각과 그 일정 시작 시각의 간격(gapMinutes)이 짧으면,
                 그 멤버는 약속이 끝나자마자 바로 다음 일정으로 이동해야 해서 여유 없이 자리를 떠야 할 수 있습니다
               - 1번(가용 인원)이 동일한 후보들 사이에서는, 이 기준이 3번(긴 시간)이나 4번(이른 시간)보다 우선합니다
                 즉 촉박한 인원이 없는 후보라면, 조금 더 짧거나 늦은 시간이어도 이 후보를 선택할 수 있습니다
            3. 동점이면(1, 2번이 같으면) 더 긴 시간대
            4. 그래도 동점이면 더 이른 시간대 (가장 낮은 우선순위입니다)

            출력 규칙:
            - recommendedSlotId는 반드시 candidates에 존재하는 slotId 중 하나여야 합니다
            - reason은 왜 이 시간대를 추천하는지 1-2문장으로 설명하세요 (인접 일정 때문에 다른 후보 대신 이걸 골랐다면 그 취지를 담아도 좋습니다)
            - alternativeNote는 다른 후보 대비 이 선택이 나은 이유를 선택적으로 덧붙이세요 (없으면 null)
            - 특정 멤버의 참여 이력이나 선호도를 근거로 언급하지 마세요
              (예: "최근 참여가 적으셨던", "~님 선호 시간대라서" 금지)
            - 특정 멤버의 인접 일정을 실명으로 언급하지 말고, "일부 멤버가 이동 시간이 촉박할 수 있어" 처럼 일반화해서 표현하세요
            - 결과 중심으로 담백하고 자연스러운 한국어로 작성하세요
            - 부담스럽지 않은 톤 유지

            [후보 있을 때 출력 형식]
            {"type": "recommendation", "recommendedSlotId": "...", "reason": "...", "alternativeNote": "..."}

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
                다음은 추천된 약속 시간 후보들입니다. 가장 적합한 시간대 하나를 선택해 추천해주세요.

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
