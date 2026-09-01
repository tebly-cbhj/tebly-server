package com.example.teblyserver.decision.service;

import com.example.teblyserver.decision.dto.response.LLMDecisionResponseDto;
import com.example.teblyserver.decision.service.DecisionHelperService.TimeRange;
import com.example.teblyserver.promise.dto.response.PromiseTimeRecommendationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 후보(slot) 구간 안에서 LLM이 좁혀 정한 finalStartTime/finalEndTime을 검증/확정하는
 * resolveFinalTimeRange()를 직접 검증한다. (긴 후보 구간을 적당히 쪼개는 로직의 핵심)
 *
 * 후보 구간: 2026-03-05 09:00~18:00 (9시간), minDurationMinutes = 60
 */
class DecisionHelperServiceTest {

    private final DecisionHelperService service =
            new DecisionHelperService(null, null, null, null, null, null, null);

    private static final LocalDateTime CANDIDATE_START = LocalDateTime.of(2026, 3, 5, 9, 0);
    private static final LocalDateTime CANDIDATE_END = LocalDateTime.of(2026, 3, 5, 18, 0);
    private static final int MIN_DURATION_MINUTES = 60;

    private static final PromiseTimeRecommendationResponse CHOSEN_RECOMMENDATION =
            new PromiseTimeRecommendationResponse(
                    CANDIDATE_START,
                    CANDIDATE_END,
                    (int) Duration.between(CANDIDATE_START, CANDIDATE_END).toMinutes(),
                    1,
                    1,
                    true,
                    "멤버 전원 가능한 시간",
                    List.of(),
                    List.of()
            );

    @Test
    @DisplayName("후보 범위 안의 정상적인 finalStartTime/finalEndTime은 그대로 사용된다")
    void withinBounds_used() {
        LLMDecisionResponseDto response = recommendation("11:30", "13:30");

        TimeRange result = service.resolveFinalTimeRange(response, CHOSEN_RECOMMENDATION, MIN_DURATION_MINUTES);

        assertThat(result.start()).isEqualTo(LocalDateTime.of(2026, 3, 5, 11, 30));
        assertThat(result.end()).isEqualTo(LocalDateTime.of(2026, 3, 5, 13, 30));
    }

    @Test
    @DisplayName("후보 구간과 동일한 finalStartTime/finalEndTime(좁히지 않는 경우)도 그대로 사용된다")
    void sameAsCandidate_used() {
        LLMDecisionResponseDto response = recommendation("09:00", "18:00");

        TimeRange result = service.resolveFinalTimeRange(response, CHOSEN_RECOMMENDATION, MIN_DURATION_MINUTES);

        assertThat(result.start()).isEqualTo(CANDIDATE_START);
        assertThat(result.end()).isEqualTo(CANDIDATE_END);
    }

    @Test
    @DisplayName("finalStartTime이 후보 시작보다 이르면(범위 이탈) 후보 전체 구간으로 대체된다")
    void beforeCandidateStart_fallsBackToFullRange() {
        LLMDecisionResponseDto response = recommendation("08:00", "10:00");

        TimeRange result = service.resolveFinalTimeRange(response, CHOSEN_RECOMMENDATION, MIN_DURATION_MINUTES);

        assertThat(result.start()).isEqualTo(CANDIDATE_START);
        assertThat(result.end()).isEqualTo(CANDIDATE_END);
    }

    @Test
    @DisplayName("finalEndTime이 후보 종료보다 늦으면(범위 이탈) 후보 전체 구간으로 대체된다")
    void afterCandidateEnd_fallsBackToFullRange() {
        LLMDecisionResponseDto response = recommendation("17:00", "19:00");

        TimeRange result = service.resolveFinalTimeRange(response, CHOSEN_RECOMMENDATION, MIN_DURATION_MINUTES);

        assertThat(result.start()).isEqualTo(CANDIDATE_START);
        assertThat(result.end()).isEqualTo(CANDIDATE_END);
    }

    @Test
    @DisplayName("finalEndTime이 finalStartTime보다 빠르면(순서 역전) 후보 전체 구간으로 대체된다")
    void reversedOrder_fallsBackToFullRange() {
        LLMDecisionResponseDto response = recommendation("14:00", "13:00");

        TimeRange result = service.resolveFinalTimeRange(response, CHOSEN_RECOMMENDATION, MIN_DURATION_MINUTES);

        assertThat(result.start()).isEqualTo(CANDIDATE_START);
        assertThat(result.end()).isEqualTo(CANDIDATE_END);
    }

    @Test
    @DisplayName("좁힌 구간의 길이가 최소 약속 시간(minDurationMinutes) 미만이면 후보 전체 구간으로 대체된다")
    void shorterThanMinDuration_fallsBackToFullRange() {
        LLMDecisionResponseDto response = recommendation("12:00", "12:30"); // 30분 < 60분

        TimeRange result = service.resolveFinalTimeRange(response, CHOSEN_RECOMMENDATION, MIN_DURATION_MINUTES);

        assertThat(result.start()).isEqualTo(CANDIDATE_START);
        assertThat(result.end()).isEqualTo(CANDIDATE_END);
    }

    @Test
    @DisplayName("finalStartTime/finalEndTime이 파싱 불가능한 형식이면 후보 전체 구간으로 대체된다")
    void unparsableFormat_fallsBackToFullRange() {
        LLMDecisionResponseDto response = recommendation("점심시간", "13:30");

        TimeRange result = service.resolveFinalTimeRange(response, CHOSEN_RECOMMENDATION, MIN_DURATION_MINUTES);

        assertThat(result.start()).isEqualTo(CANDIDATE_START);
        assertThat(result.end()).isEqualTo(CANDIDATE_END);
    }

    @Test
    @DisplayName("finalStartTime/finalEndTime이 아예 없으면(null) 후보 전체 구간이 사용된다")
    void missingFinalTimes_usesFullRange() {
        LLMDecisionResponseDto response =
                LLMDecisionResponseDto.recommendation("slot-1", "이유", null, null, null, null);

        TimeRange result = service.resolveFinalTimeRange(response, CHOSEN_RECOMMENDATION, MIN_DURATION_MINUTES);

        assertThat(result.start()).isEqualTo(CANDIDATE_START);
        assertThat(result.end()).isEqualTo(CANDIDATE_END);
    }

    private static LLMDecisionResponseDto recommendation(String finalStartTime, String finalEndTime) {
        return LLMDecisionResponseDto.recommendation("slot-1", "이유", null, finalStartTime, finalEndTime, null);
    }
}
