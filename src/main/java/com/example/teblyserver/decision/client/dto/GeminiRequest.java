package com.example.teblyserver.decision.client.dto;

import java.util.List;

/**
 * Gemini generateContent 요청 바디.
 * {
 *   "contents": [{"role": "user", "parts": [{"text": "..."}]}],
 *   "systemInstruction": {"parts": [{"text": "..."}]},
 *   "generationConfig": {"responseMimeType": "application/json"}
 * }
 */
public record GeminiRequest(
        List<Content> contents,
        SystemInstruction systemInstruction,
        GenerationConfig generationConfig
) {

    public record Content(String role, List<Part> parts) {
    }

    public record Part(String text) {
    }

    public record SystemInstruction(List<Part> parts) {
    }

    public record GenerationConfig(String responseMimeType) {
    }

    public static GeminiRequest of(String systemPrompt, String userPrompt) {
        return new GeminiRequest(
                List.of(new Content("user", List.of(new Part(userPrompt)))),
                new SystemInstruction(List.of(new Part(systemPrompt))),
                new GenerationConfig("application/json")
        );
    }
}
