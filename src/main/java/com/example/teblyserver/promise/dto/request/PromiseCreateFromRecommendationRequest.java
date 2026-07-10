package com.example.teblyserver.promise.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record PromiseCreateFromRecommendationRequest(
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

        LocalTime searchStartTime,

        LocalTime searchEndTime,

        // 추가: 프론트가 선택한 원래 추천 카드의 시작 시간
        @NotNull(message = "원본 추천 시작 시간은 필수입니다.")
        LocalDateTime recommendedStartTime,

        // 추가: 프론트가 선택한 원래 추천 카드의 종료 시간
        @NotNull(message = "원본 추천 종료 시간은 필수입니다.")
        LocalDateTime recommendedEndTime,

        // 최종 약속 시작 시간
        LocalDateTime selectedStartTime,

        // 최종 약속 종료 시간
        LocalDateTime selectedEndTime,

        @Size(max = 50, message = "장소는 50자를 넘을 수 없습니다.")
        String location,

        List<@Min(value = 0, message = "알림 시간은 0분 이상이어야 합니다.") Integer> notificationLeadMinutes,

        @NotNull(message = "최소 시간 설정은 필수입니다.")
        @Min(value = 30, message = "최소 약속 시간은 30분 이상이어야 합니다.")
        Integer minDuration,

        // null이면 추천 서비스에서 기본값 EARLIEST로 처리됨
        PromiseTimeRecommendationSortType sortType,

        @NotEmpty(message = "추천 대상 멤버는 최소 1명 이상이어야 합니다.")
        List<@NotNull(message = "추천 대상 멤버 ID는 null일 수 없습니다.") Long> selectedMemberIds
) {

        public PromiseCreateFromRecommendationRequest {
                if (searchStartTime == null) {
                        searchStartTime = LocalTime.of(9, 0); // 기본값: 오전 9시
                }
                if (searchEndTime == null) {
                        searchEndTime = LocalTime.of(22, 0); // 기본값: 오후 10시
                }
        }
}
