package com.example.teblyserver.promise.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PromiseCreateRequest(
        @NotBlank(message = "약속 이름은 필수입니다.")
        @Size(max = 50, message = "약속 이름은 50자를 넘을 수 없습니다.")
        String title,

        @Size(max = 255, message = "메모는 255자를 넘을 수 없습니다.")
        String comment,

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @NotNull(message = "조율 시작일은 필수입니다.")
        LocalDate proposeStartDate,

        @NotNull(message = "조율 종료일은 필수입니다.")
        LocalDate proposeEndDate,

        @NotNull(message = "제안 시작 시간은 필수입니다.")
        LocalDateTime startTime,

        @NotNull(message = "제안 종료 시간은 필수입니다.")
        LocalDateTime endTime,

        @Size(max = 50, message = "장소는 50자를 넘을 수 없습니다.")
        String location,

        @Min(value = 0, message = "알림 시간은 0분 이상이어야 합니다.")
        Integer notificationLeadMinutes,

        @NotNull(message = "최소 시간 설정은 필수입니다.")
        Integer minDuration,

        @NotEmpty(message = "초대할 멤버를 최소 1명 이상 선택해야 합니다.")
        List<@NotNull(message = "초대할 멤버 ID는 null일 수 없습니다.") Long> inviteeIds
) {
}
