package com.example.teblyserver.decision.dto.request;

import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendationSortType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 결정 도우미(Decision Helper) 요청 바디.
 * 추천 알고리즘 실행에 필요한 정보(추천 API와 동일)에 더해,
 * LLM이 실제로 고른 시간으로 약속을 바로 생성할 수 있도록 약속 생성에 필요한 정보까지 함께 받는다.
 */
public record DecisionHelperPromiseRequest(
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

        @NotNull(message = "하루 탐색 시작 시간은 필수입니다.")
        LocalTime searchStartTime,

        @NotNull(message = "하루 탐색 종료 시간은 필수입니다.")
        LocalTime searchEndTime,

        @NotNull(message = "최소 약속 시간은 필수입니다.")
        @Min(value = 30, message = "최소 약속 시간은 30분 이상이어야 합니다.")
        Integer minDuration,

        // 안 보내면 알고리즘 기본값(EARLIEST)으로 처리
        PromiseTimeRecommendationSortType sortType,

        @Size(max = 50, message = "장소는 50자를 넘을 수 없습니다.")
        String location,

        List<@Min(value = 0, message = "알림 시간은 0분 이상이어야 합니다.") Integer> notificationLeadMinutes,

        @NotEmpty(message = "추천 대상 멤버는 최소 1명 이상이어야 합니다.")
        List<Long> selectedMemberIds
) {
}
