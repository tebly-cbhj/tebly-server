package com.example.teblyserver.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "OCR 스케줄 추출 응답")
public class ScheduleOcrResponse {

    @Schema(description = "OCR로 추출된 전체 원본 텍스트")
    private final String rawText;

    @Schema(description = "파싱된 일정 목록 (파싱 실패 시 빈 배열)")
    private final List<OcrScheduleItem> schedules;
}
