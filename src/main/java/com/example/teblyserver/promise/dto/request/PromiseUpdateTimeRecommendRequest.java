package com.example.teblyserver.promise.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 약속 수정 화면에서 "추천으로 시간 수정"을 누른 뒤,
 * 현재 약속 멤버 기준으로 빈 시간 추천을 다시 받을 때 사용하는 Request DTO.
 *
 * 기존 빈 시간 추천 API와 거의 비슷하지만,
 * selectedMemberIds를 프론트에서 받지 않는다.
 *
 * 이유:
 * - 약속 수정 시점에는 이미 PromiseMember가 존재함
 * - 따라서 추천 대상 멤버는 백엔드가 현재 약속 멤버에서 직접 뽑는 것이 안전함
 */
public record PromiseUpdateTimeRecommendRequest(

        @NotNull(message = "추천 시작일은 필수입니다.")
        LocalDate proposeStartDate,

        @NotNull(message = "추천 종료일은 필수입니다.")
        LocalDate proposeEndDate,

        LocalTime searchStartTime,

        LocalTime searchEndTime,

        @NotNull(message = "최소 약속 시간은 필수입니다.")
        @Min(value = 30, message = "최소 약속 시간은 30분 이상이어야 합니다.")
        Integer minDuration,

        PromiseTimeRecommendationSortType sortType
) {

        public PromiseUpdateTimeRecommendRequest {
                if (searchStartTime == null) {
                        searchStartTime = LocalTime.of(9, 0); // 기본값: 오전 9시
                }
                if (searchEndTime == null) {
                        searchEndTime = LocalTime.of(23, 30); // 기본값: 오후 10시
                }
        }
}
