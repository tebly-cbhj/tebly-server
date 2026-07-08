package com.example.teblyserver.decision.client;

import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.config.GeminiProperties;
import com.example.teblyserver.decision.client.dto.GeminiRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Google Gemini(generateContent) REST API 호출 클라이언트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;

    /**
     * 시스템/유저 프롬프트로 Gemini를 호출하고, 모델이 생성한 텍스트(candidates[0].content.parts[0].text)를 반환한다.
     */
    public String generate(String systemPrompt, String userPrompt) {
        String requestBody = serializeRequest(systemPrompt, userPrompt);

        Request request = new Request.Builder()
                .url(geminiProperties.getUrl())
                .addHeader("x-goog-api-key", geminiProperties.getKey())
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .build();

        log.debug("Gemini API 호출: url={}", geminiProperties.getUrl());

        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody body = response.body();

            if (!response.isSuccessful() || body == null) {
                log.error("Gemini API 응답 실패: code={}", response.code());
                throw new CustomException(ErrorCode.GEMINI_API_CALL_FAILED);
            }

            return extractText(body.string());

        } catch (CustomException e) {
            throw e;
        } catch (IOException e) {
            log.error("Gemini API 통신 오류: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.GEMINI_API_CALL_FAILED);
        } catch (Exception e) {
            log.error("Gemini API 알 수 없는 오류: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
    }

    private String serializeRequest(String systemPrompt, String userPrompt) {
        try {
            return objectMapper.writeValueAsString(GeminiRequest.of(systemPrompt, userPrompt));
        } catch (IOException e) {
            log.error("Gemini 요청 직렬화 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode textNode = root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text");

            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                log.error("Gemini 응답에서 텍스트 추출 실패: body={}", responseBody);
                throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_FAILED);
            }

            return textNode.asText();

        } catch (CustomException e) {
            throw e;
        } catch (IOException e) {
            log.error("Gemini 응답 파싱 오류: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_FAILED);
        }
    }
}
