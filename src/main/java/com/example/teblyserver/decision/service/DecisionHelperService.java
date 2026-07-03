package com.example.teblyserver.decision.service;

import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.decision.client.GeminiClient;
import com.example.teblyserver.decision.dto.AdjacentScheduleDto;
import com.example.teblyserver.decision.dto.CandidateSlotDto;
import com.example.teblyserver.decision.dto.MemberAvailabilityDto;
import com.example.teblyserver.decision.dto.MemberSummaryDto;
import com.example.teblyserver.decision.dto.response.LLMDecisionResponseDto;
import com.example.teblyserver.promise.dto.internal.BusyScheduleTimeRange;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendRequest;
import com.example.teblyserver.promise.dto.response.PromiseRecommendationMemberResponse;
import com.example.teblyserver.promise.dto.response.PromiseTimeRecommendationResponse;
import com.example.teblyserver.promise.service.PromiseRecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * "결정이 어려울 때" 도우미 핵심 흐름.
 * 캐시 없이 매 호출마다 추천 알고리즘(PromiseRecommendationService)을 직접 재실행해
 * 그 결과로 프롬프트를 만들고 Gemini를 호출한다.
 *
 * NOTE: 현재 알고리즘(PromiseRecommendationService)은 가용성 기반 데이터만 제공한다.
 *       추후 선호도/참여도 가중치가 알고리즘에 추가되면 후보 데이터에 scoreBreakdown 필드를
 *       확장하고, 프롬프트에도 해당 정보를 반영할 예정이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionHelperService {

    // 슬롯 앞/뒤로 인접 일정을 찾을 때 살펴볼 버퍼 (슬롯 시작 전 1시간 ~ 슬롯 종료 후 1시간)
    private static final long ADJACENT_SCHEDULE_BUFFER_MINUTES = 60;

    private final PromiseRecommendationService promiseRecommendationService;
    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final SanitizeService sanitizeService;
    private final ObjectMapper objectMapper;

    public LLMDecisionResponseDto decide(Long userId, Long roomId, PromiseTimeRecommendRequest request) {

        List<PromiseTimeRecommendationResponse> recommendations =
                promiseRecommendationService.recommendPromiseTimes(userId, roomId, request);

        boolean noCandidate = recommendations.isEmpty();

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt;
        List<CandidateSlotDto> candidates = List.of();

        if (noCandidate) {
            // TODO: 알고리즘에서 멤버별 freeRanges 직접 추출이 어려워 일단 빈 리스트로 전달
            List<MemberAvailabilityDto> memberAvailability = List.of();
            userPrompt = promptBuilder.buildAlternativePrompt(
                    memberAvailability,
                    request.proposeStartDate(),
                    request.proposeEndDate()
            );
        } else {
            candidates = toCandidateSlots(recommendations);
            userPrompt = promptBuilder.buildComparisonPrompt(candidates);
        }

        String llmText = geminiClient.generate(systemPrompt, userPrompt);
        LLMDecisionResponseDto parsed = parse(llmText);

        if (!noCandidate) {
            parsed = validateRecommendedSlotId(parsed, candidates);
        }

        return sanitizeService.sanitize(parsed, collectMemberNames(candidates));
    }

    // sanitize에서 reason/alternativeNote에 실명이 노출됐는지 확인할 때 쓸 멤버 이름 목록
    private List<String> collectMemberNames(List<CandidateSlotDto> candidates) {
        return candidates.stream()
                .flatMap(candidate -> Stream.concat(
                        candidate.availableMembers().stream(),
                        candidate.unavailableMembers().stream()
                ))
                .map(MemberSummaryDto::name)
                .distinct()
                .toList();
    }

    // PromiseTimeRecommendationResponse → CandidateSlotDto 변환 (slotId는 순서대로 "slot-N" 부여)
    private List<CandidateSlotDto> toCandidateSlots(List<PromiseTimeRecommendationResponse> recommendations) {
        List<CandidateSlotDto> candidateSlots = new ArrayList<>();

        for (int i = 0; i < recommendations.size(); i++) {
            PromiseTimeRecommendationResponse recommendation = recommendations.get(i);

            candidateSlots.add(
                    new CandidateSlotDto(
                            "slot-" + (i + 1),
                            recommendation.startTime(),
                            recommendation.endTime(),
                            recommendation.durationMinutes(),
                            recommendation.allAvailable(),
                            recommendation.availableMemberCount(),
                            recommendation.totalMemberCount(),
                            toAvailableMemberSummaries(recommendation),
                            toUnavailableMemberSummaries(recommendation)
                    )
            );
        }

        return candidateSlots;
    }

    /**
     * 가용 멤버는 슬롯 앞/뒤 인접 일정(beforeSlot/afterSlot)까지 함께 채워서 반환한다.
     * (예: 슬롯 직전에 헬스 일정이 있어 촉박하게 참여해야 하는 경우 LLM이 판단에 활용)
     */
    private List<MemberSummaryDto> toAvailableMemberSummaries(PromiseTimeRecommendationResponse recommendation) {
        List<PromiseRecommendationMemberResponse> availableMembers = recommendation.availableMembers();

        if (availableMembers.isEmpty()) {
            return List.of();
        }

        LocalDateTime slotStart = recommendation.startTime();
        LocalDateTime slotEnd = recommendation.endTime();

        List<Long> memberIds = availableMembers.stream()
                .map(PromiseRecommendationMemberResponse::userId)
                .toList();

        List<BusyScheduleTimeRange> nearbySchedules = promiseRecommendationService.findExpandedSchedulesInWindow(
                memberIds,
                slotStart.toLocalDate(),
                slotStart.minusMinutes(ADJACENT_SCHEDULE_BUFFER_MINUTES),
                slotEnd.plusMinutes(ADJACENT_SCHEDULE_BUFFER_MINUTES)
        );

        Map<Long, List<BusyScheduleTimeRange>> schedulesByUserId = nearbySchedules.stream()
                .collect(Collectors.groupingBy(BusyScheduleTimeRange::userId));

        return availableMembers.stream()
                .map(member -> {
                    List<BusyScheduleTimeRange> memberSchedules =
                            schedulesByUserId.getOrDefault(member.userId(), List.of());

                    return new MemberSummaryDto(
                            member.nickname(),
                            findAdjacentSchedule(memberSchedules, slotStart, true),
                            findAdjacentSchedule(memberSchedules, slotEnd, false)
                    );
                })
                .toList();
    }

    private List<MemberSummaryDto> toUnavailableMemberSummaries(PromiseTimeRecommendationResponse recommendation) {
        return recommendation.unavailableMembers().stream()
                .map(member -> new MemberSummaryDto(member.nickname(), null, null))
                .toList();
    }

    /**
     * before=true면 slotStart 이전에 끝나면서 가장 가까운(=가장 늦게 끝나는) 일정을 찾는다.
     * before=false면 slotEnd 이후에 시작하면서 가장 가까운(=가장 빨리 시작하는) 일정을 찾는다.
     * 슬롯 자체와 겹치는 일정은 방어적으로 제외한다 (가용 멤버라면 원래 없어야 함).
     */
    private AdjacentScheduleDto findAdjacentSchedule(
            List<BusyScheduleTimeRange> schedules,
            LocalDateTime boundary,
            boolean before
    ) {
        Comparator<BusyScheduleTimeRange> nearestFirst = before
                ? Comparator.comparing(BusyScheduleTimeRange::endTime).reversed()
                : Comparator.comparing(BusyScheduleTimeRange::startTime);

        return schedules.stream()
                .filter(schedule -> before ? !schedule.endTime().isAfter(boundary) : !schedule.startTime().isBefore(boundary))
                .min(nearestFirst)
                .map(schedule -> {
                    long gapMinutes = before
                            ? Duration.between(schedule.endTime(), boundary).toMinutes()
                            : Duration.between(boundary, schedule.startTime()).toMinutes();

                    return new AdjacentScheduleDto(schedule.title(), schedule.startTime(), schedule.endTime(), gapMinutes);
                })
                .orElse(null);
    }

    /**
     * LLM이 candidates에 존재하지 않는 slotId를 응답으로 줄 가능성에 대비한 방어 로직.
     * 일치하는 후보가 없으면 정렬 1순위(candidates의 첫 번째 항목)로 대체한다.
     */
    private LLMDecisionResponseDto validateRecommendedSlotId(LLMDecisionResponseDto parsed, List<CandidateSlotDto> candidates) {
        boolean isValidSlotId = candidates.stream()
                .anyMatch(candidate -> candidate.slotId().equals(parsed.recommendedSlotId()));

        if (isValidSlotId) {
            return parsed;
        }

        String fallbackSlotId = candidates.get(0).slotId();
        log.warn("Gemini가 candidates에 없는 recommendedSlotId({})를 응답 → 1순위 후보({})로 대체합니다.",
                parsed.recommendedSlotId(), fallbackSlotId);

        return LLMDecisionResponseDto.recommendation(
                fallbackSlotId,
                parsed.reason(),
                parsed.alternativeNote(),
                parsed.fallbackUsed()
        );
    }

    private LLMDecisionResponseDto parse(String llmText) {
        // 모델이 규칙을 어기고 ```json ... ``` 코드블록으로 감싸는 경우를 방어적으로 제거.
        String cleaned = stripCodeFence(llmText);
        try {
            return objectMapper.readValue(cleaned, LLMDecisionResponseDto.class);
        } catch (Exception e) {
            log.error("Gemini 결과 JSON 파싱 실패: text={}", llmText, e);
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_FAILED);
        }
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            // 앞쪽 ```json 또는 ``` 제거
            int firstLineBreak = trimmed.indexOf('\n');
            if (firstLineBreak != -1) {
                trimmed = trimmed.substring(firstLineBreak + 1);
            }
            // 뒤쪽 ``` 제거
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
