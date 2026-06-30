package com.example.teblyserver.promise.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PromiseTimeRecommendRequest(

        @NotNull(message = "추천 시작일은 필수입니다.")
        LocalDate proposeStartDate,

        @NotNull(message = "추천 종료일은 필수입니다.")
        LocalDate proposeEndDate,

        @NotNull(message = "하루 탐색 시작 시간은 필수입니다.")
        LocalTime searchStartTime,

        @NotNull(message = "하루 탐색 종료 시간은 필수입니다.")
        LocalTime searchEndTime,

        @NotNull(message = "최소 약속 시간은 필수입니다.")
        @Min(value = 30, message = "최소 약속 시간은 30분 이상이어야 합니다.")
        Integer minDuration,

        //sortType은 필수로 안 둬도 됨
        //안 보내면 기본값을 EARLIEST로 처리
        PromiseTimeRecommendationSortType sortType,

        @NotEmpty(message = "추천 대상 멤버는 최소 1명 이상이어야 합니다.")
        List<Long> selectedMemberIds
) {
}
