package com.example.teblyserver.schedule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "OCR 일정 확정 저장 응답")
public class ScheduleOcrConfirmResponse {

    @Schema(description = "저장된 일정 수", example = "3")
    private final int savedCount;
}
