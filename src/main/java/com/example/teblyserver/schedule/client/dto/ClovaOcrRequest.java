package com.example.teblyserver.schedule.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * CLOVA OCR API 요청 바디 DTO
 * POST {api-url}
 * Header: X-OCR-SECRET
 */
@Getter
@Builder
public class ClovaOcrRequest {

    private final String version;
    private final String requestId;
    private final long timestamp;
    private final List<ImageData> images;

    @Getter
    @Builder
    public static class ImageData {
        private final String format; // jpg | png
        private final String name;
        private final String data;  // base64 인코딩된 이미지
    }
}
