package com.example.teblyserver.schedule.dto;

import com.example.teblyserver.schedule.domain.OcrCategoryType;
import com.example.teblyserver.schedule.domain.RepeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "OCR로 추출된 일정 단건")
public class OcrScheduleItem {

    @Schema(description = "요일 (MON~SUN)", example = "MON")
    private final String dayOfWeek;

    @Schema(description = "시작 시간 (HH:mm)", example = "09:00")
    private final String startTime;

    @Schema(description = "종료 시간 (HH:mm)", example = "10:30")
    private final String endTime;

    @Schema(description = "강의명 또는 일정 제목", example = "자료구조")
    private final String title;

    @Schema(description = "텍스트 기반 추정 카테고리 (LECTURE/WORK/ETC)", example = "LECTURE")
    private final OcrCategoryType categoryType;

    @Schema(description = "반복 유형 (시간표 특성상 매주 반복으로 고정)", example = "WEEKLY")
    private final RepeatType repeatType;
}
