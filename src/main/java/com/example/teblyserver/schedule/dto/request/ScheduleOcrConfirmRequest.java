package com.example.teblyserver.schedule.dto.request;

import com.example.teblyserver.schedule.domain.RepeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "OCR 추출 일정 확정 저장 요청")
public class ScheduleOcrConfirmRequest {

    @NotNull(message = "일정 목록은 필수입니다.")
    @Size(min = 1, message = "일정 목록은 비어있을 수 없습니다.")
    @Schema(description = "확정 저장할 일정 목록")
    private List<@Valid ScheduleItem> schedules;

    @Getter
    @NoArgsConstructor
    @Schema(description = "일정 단건")
    public static class ScheduleItem {

        @NotBlank(message = "제목은 필수입니다.")
        @Schema(description = "일정 제목", example = "프로그래밍언어")
        private String title;

        @NotNull(message = "시작 시간은 필수입니다.")
        @Schema(description = "시작 일시", example = "2026-03-03T10:30:00")
        private LocalDateTime startTime;

        @NotNull(message = "종료 시간은 필수입니다.")
        @Schema(description = "종료 일시", example = "2026-03-03T12:00:00")
        private LocalDateTime endTime;

        @NotNull(message = "반복 유형은 필수입니다.")
        @Schema(description = "반복 유형", example = "WEEKLY")
        private RepeatType repeatType;
    }
}
