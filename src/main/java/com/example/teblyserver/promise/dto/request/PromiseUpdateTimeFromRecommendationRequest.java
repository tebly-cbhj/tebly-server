package com.example.teblyserver.promise.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;


/**
 * 추천 결과 중 하나를 선택해서 실제 약속 시간을 수정할 때 사용하는 Request DTO.
 *
 * recommendedStartTime / recommendedEndTime
 * - 사용자가 선택한 "원본 추천 카드"의 시간
 * - 백엔드는 이 시간이 실제 추천 결과에 존재하는지 다시 검증함
 *
 * selectedStartTime / selectedEndTime
 * - 사용자가 원본 추천 카드 안에서 최종으로 줄여 선택한 시간
 * - 시간 수정을 하지 않았다면 null 가능
 * - 둘 다 null이면 recommendedStartTime/recommendedEndTime을 최종 약속 시간으로 사용
 */
public record PromiseUpdateTimeFromRecommendationRequest(
        @NotNull(message = "조율 시작일은 필수입니다.")
        LocalDate proposeStartDate,

        @NotNull(message = "조율 종료일은 필수입니다.")
        LocalDate proposeEndDate,

        @NotNull(message = "하루 탐색 시작 시간은 필수입니다.")
        LocalTime searchStartTime,

        @NotNull(message = "하루 탐색 종료 시간은 필수입니다.")
        LocalTime searchEndTime,

        @NotNull(message = "원본 추천 시작 시간은 필수입니다.")
        LocalDateTime recommendedStartTime,

        @NotNull(message = "원본 추천 종료 시간은 필수입니다.")
        LocalDateTime recommendedEndTime,

        // 시간 수정 안 하면 null 가능
        LocalDateTime selectedStartTime,

        // 시간 수정 안 하면 null 가능
        LocalDateTime selectedEndTime,

        @NotNull(message = "최소 시간 설정은 필수입니다.")
        @Min(value = 30, message = "최소 약속 시간은 30분 이상이어야 합니다.")
        Integer minDuration,

        PromiseTimeRecommendationSortType sortType
) {
}
