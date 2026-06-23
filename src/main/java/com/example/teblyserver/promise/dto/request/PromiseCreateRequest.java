package com.example.teblyserver.promise.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PromiseCreateRequest(
        @NotNull(message = "방 ID는 필수입니다.")
        Long roomId,

        @NotBlank(message = "약속 이름은 필수입니다.")
        @Size(max = 50, message = "약속 이름은 50자를 넘을 수 없습니다.")
        String title,

        @Size(max = 255, message = "메모는 255자를 넘을 수 없습니다.")
        String comment,

        Long categoryId,

        @NotNull(message = "조율 시작일은 필수입니다.")
        LocalDate proposeStartDate,

        @NotNull(message = "조율 종료일은 필수입니다.")
        LocalDate proposeEndDate,

        @NotNull(message = "최소 시간 설정은 필수입니다.")
        Integer minDuration
) {
}
