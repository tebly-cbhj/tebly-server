package com.example.teblyserver.decision.service;

import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.decision.client.GeminiClient;
import com.example.teblyserver.decision.dto.DecisionCacheDto;
import com.example.teblyserver.decision.dto.response.LLMDecisionResponseDto;
import com.example.teblyserver.decision.dummy.DummyDecisionDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * "결정이 어려울 때" 도우미 핵심 흐름.
 * roomId로 Redis 캐시(추천 알고리즘 결과)를 조회 → 프롬프트 생성 → Gemini 호출 → 응답 파싱 → sanitize → 반환.
 *
 * NOTE: 현재 알고리즘(PromiseRecommendationService)은 가용성 기반 데이터만 제공한다.
 *       추후 선호도/참여도 가중치가 알고리즘에 추가되면 후보 데이터에 scoreBreakdown 필드를
 *       확장하고, 프롬프트에도 해당 정보를 반영할 예정이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionHelperService {

    private final DecisionCacheService decisionCacheService;
    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final SanitizeService sanitizeService;
    private final ObjectMapper objectMapper;

    public LLMDecisionResponseDto decide(Long roomId) {

        DecisionCacheDto cache = loadDecisionCache(roomId);

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt;

        if (cache.noCandidate()) {
            userPrompt = promptBuilder.buildAlternativePrompt(
                    cache.memberAvailability(),
                    cache.rangeFrom(),
                    cache.rangeTo()
            );
        } else {
            userPrompt = promptBuilder.buildComparisonPrompt(cache.candidates());
        }

        String llmText = geminiClient.generate(systemPrompt, userPrompt);
        LLMDecisionResponseDto parsed = parse(llmText);

        return sanitizeService.sanitize(parsed);
    }

    private DecisionCacheDto loadDecisionCache(Long roomId) {
        try {
            return decisionCacheService.getDecisionCache(roomId);
        } catch (CustomException e) {
            // TODO: 알고리즘 연동 완료 후 제거 예정 - 캐시 미스 시 테스트용 더미로 폴백
            if (e.getErrorCode() == ErrorCode.DECISION_CACHE_NOT_FOUND) {
                log.warn("결정 캐시 미스 → 더미 데이터로 폴백합니다. roomId={}", roomId);
                return DummyDecisionDataFactory.dummyDecisionCache();
            }
            throw e;
        }
    }

    private LLMDecisionResponseDto parse(String llmText) {
        // 모델이 규칙을 어기고 ```json ... ``` 코드블록으로 감싸는 경우를 방어적으로 제거.
        String cleaned = stripCodeFence(llmText);
        try {
            return objectMapper.readValue(cleaned, LLMDecisionResponseDto.class);
        } catch (Exception e) {
            log.error("Gemini 결과 JSON 파싱 실패: text={}", llmText, e);
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_FAILED);
        }
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            // 앞쪽 ```json 또는 ``` 제거
            int firstLineBreak = trimmed.indexOf('\n');
            if (firstLineBreak != -1) {
                trimmed = trimmed.substring(firstLineBreak + 1);
            }
            // 뒤쪽 ``` 제거
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
