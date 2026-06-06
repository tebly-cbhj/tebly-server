package com.example.teblyserver.schedule.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * CLOVA OCR API 응답 바디 DTO
 * 미사용 필드는 @JsonIgnoreProperties 로 무시
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClovaOcrApiResponse {

    private String version;
    private String requestId;
    private long timestamp;
    private List<ImageResult> images;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageResult {
        private String uid;
        private String name;
        private String inferResult; // SUCCESS | ERROR
        private String message;
        private List<Field> fields;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Field {
        private String inferText;
        private double inferConfidence;
        private boolean lineBreak;      // true면 이 필드 다음에 줄바꿈
        private BoundingPoly boundingPoly; // 텍스트 위치 좌표 (4개 꼭짓점)
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BoundingPoly {
        private List<Vertex> vertices;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Vertex {
        private double x;
        private double y;
    }
}
