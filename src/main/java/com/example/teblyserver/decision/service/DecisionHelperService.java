package com.example.teblyserver.decision.service;

import com.example.teblyserver.chat.service.ChatMessageService;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.decision.client.GeminiClient;
import com.example.teblyserver.decision.dto.AdjacentScheduleDto;
import com.example.teblyserver.decision.dto.CandidateSlotDto;
import com.example.teblyserver.decision.dto.MemberAvailabilityDto;
import com.example.teblyserver.decision.dto.MemberSummaryDto;
import com.example.teblyserver.decision.dto.request.DecisionHelperPromiseRequest;
import com.example.teblyserver.decision.dto.response.LLMDecisionResponseDto;
import com.example.teblyserver.promise.dto.internal.BusyScheduleTimeRange;
import com.example.teblyserver.promise.dto.request.PromiseCreateRequest;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendRequest;
import com.example.teblyserver.promise.dto.response.PromiseRecommendationMemberResponse;
import com.example.teblyserver.promise.dto.response.PromiseTimeRecommendationResponse;
import com.example.teblyserver.promise.service.PromiseRecommendationService;
import com.example.teblyserver.promise.service.PromiseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * "결정이 어려울 때" 도우미 핵심 흐름.
 * 캐시 없이 매 호출마다 추천 알고리즘(PromiseRecommendationService)을 직접 재실행해
 * 그 결과에 멤버별 인접 일정(beforeSlot/afterSlot)을 덧붙여 프롬프트를 만들고 Gemini를 호출한다.
 *
 * 후보가 있는 경우(type=recommendation)에는 LLM이 고른 시간으로 실제 약속을 생성하고
 * (멤버 상황별로 다른 초대 메시지 사용), 방 채팅에 선택 사유를 공유한다.
 * 후보가 없는 경우(type=alternative)는 LLM이 제안한 시간이 실제 후보로 검증된 게 아니므로
 * 약속을 자동 생성하지 않고 제안만 반환한다.
 *
 * NOTE: 알고리즘(PromiseRecommendationService) 자체는 여전히 가용성 기반 데이터만 산출한다.
 *       참여도 등 추가 가중치가 알고리즘에 들어가면 그때 후보 데이터에 scoreBreakdown 필드를
 *       확장할 예정이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionHelperService {

    // 슬롯 앞/뒤로 인접 일정을 찾을 때 살펴볼 버퍼 (슬롯 시작 전 1시간 ~ 슬롯 종료 후 1시간)
    private static final long ADJACENT_SCHEDULE_BUFFER_MINUTES = 60;

    // fallback 정렬에서 "촉박한 인접 일정"으로 간주할 gapMinutes 임계값 (프롬프트의 "30분 이하" 기준과 동일)
    private static final long TIGHT_SCHEDULE_THRESHOLD_MINUTES = 30;

    private static final DateTimeFormatter CHAT_SUMMARY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter CHAT_SUMMARY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final PromiseRecommendationService promiseRecommendationService;
    private final PromiseService promiseService;
    private final ChatMessageService chatMessageService;
    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final SanitizeService sanitizeService;
    private final ObjectMapper objectMapper;

    public LLMDecisionResponseDto decide(Long userId, Long roomId, DecisionHelperPromiseRequest request) {

        PromiseTimeRecommendRequest recommendRequest = new PromiseTimeRecommendRequest(
                request.proposeStartDate(),
                request.proposeEndDate(),
                request.searchStartTime(),
                request.searchEndTime(),
                request.minDuration(),
                request.sortType(),
                request.selectedMemberIds()
        );

        List<PromiseTimeRecommendationResponse> recommendations =
                promiseRecommendationService.recommendPromiseTimes(userId, roomId, recommendRequest);

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

        LLMDecisionResponseDto sanitized = sanitizeService.sanitize(parsed, collectMemberNames(candidates));

        // 후보가 없는 경우(type=alternative)는 LLM이 제안한 시간이 검증된 후보가 아니므로
        // 약속을 자동 생성하지 않고 제안만 반환한다.
        if (noCandidate) {
            return sanitized;
        }

        PromiseTimeRecommendationResponse chosenRecommendation =
                resolveChosenRecommendation(sanitized.recommendedSlotId(), recommendations);

        Long promiseId = createPromiseFromDecision(userId, roomId, request, chosenRecommendation);

        String chatSummary = buildChatSummary(sanitized, chosenRecommendation);
        chatMessageService.postDecisionSummary(roomId, userId, chatSummary);

        return sanitized.withPromiseId(promiseId);
    }

    // "slot-N" 형식의 slotId를 원래 추천 결과(N-1번째)로 되돌린다. toCandidateSlots()가 부여한 순서와 항상 일치한다.
    private PromiseTimeRecommendationResponse resolveChosenRecommendation(
            String slotId,
            List<PromiseTimeRecommendationResponse> recommendations
    ) {
        try {
            int index = Integer.parseInt(slotId.substring(slotId.lastIndexOf('-') + 1)) - 1;
            if (index < 0 || index >= recommendations.size()) {
                throw new CustomException(ErrorCode.NO_CANDIDATE_AVAILABLE);
            }
            return recommendations.get(index);
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.NO_CANDIDATE_AVAILABLE);
        }
    }

    /**
     * 결정이가 고른 시간으로 실제 약속을 생성한다.
     * 생성자(userId)는 반드시 그 시간에 가능해야 한다.
     * 초대 대상은 가능한 멤버뿐 아니라 불가능한 멤버도 포함한다 — 불가능한 멤버에게는
     * PromiseService.buildInvitationMessage()가 그 멤버의 충돌 일정 카테고리를 보고
     * "조정 가능할까요?" 또는 "참석이 어려울 수 있어요"로 메시지를 다르게 보낸다.
     */
    private Long createPromiseFromDecision(
            Long userId,
            Long roomId,
            DecisionHelperPromiseRequest request,
            PromiseTimeRecommendationResponse chosenRecommendation
    ) {
        List<Long> availableMemberIds = chosenRecommendation.availableMembers().stream()
                .map(PromiseRecommendationMemberResponse::userId)
                .distinct()
                .toList();

        if (!availableMemberIds.contains(userId)) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_MEMBER);
        }

        List<Long> inviteeIds = Stream.concat(
                        chosenRecommendation.availableMembers().stream(),
                        chosenRecommendation.unavailableMembers().stream()
                )
                .map(PromiseRecommendationMemberResponse::userId)
                .distinct()
                .filter(memberId -> !memberId.equals(userId))
                .toList();

        if (inviteeIds.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_MEMBER);
        }

        PromiseCreateRequest createRequest = new PromiseCreateRequest(
                request.title(),
                request.comment(),
                request.categoryId(),
                request.proposeStartDate(),
                request.proposeEndDate(),
                chosenRecommendation.startTime(),
                chosenRecommendation.endTime(),
                request.location(),
                request.notificationLeadMinutes(),
                request.minDuration(),
                inviteeIds
        );

        return promiseService.createPromiseFromDecisionHelper(userId, roomId, createRequest);
    }

    private String buildChatSummary(LLMDecisionResponseDto response, PromiseTimeRecommendationResponse chosenRecommendation) {
        // 약속은 항상 하루 안에서 끝나므로 날짜는 한 번만 표시한다.
        String date = chosenRecommendation.startTime().format(CHAT_SUMMARY_DATE_FORMAT);
        String startTime = chosenRecommendation.startTime().format(CHAT_SUMMARY_TIME_FORMAT);
        String endTime = chosenRecommendation.endTime().format(CHAT_SUMMARY_TIME_FORMAT);

        return "결정이의 추천시간은 " + date + " " + startTime + " ~ " + endTime + " 이에요!\n"
                + response.reason() + "\n"
                + "결정이가 약속 초대장을 전달했어요.\n"
                + "알림창에서 확인해주세요.";
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
     * 일치하는 후보가 없으면, candidates.get(0)(알고리즘의 sortType 정렬 순서) 대신
     * 프롬프트에 지시한 우선순위(가용인원 → 인접일정 촉박도 → 긴 시간 → 이른 시간)와
     * 동일한 기준으로 별도 정렬한 첫 번째 후보로 대체한다.
     */
    private LLMDecisionResponseDto validateRecommendedSlotId(LLMDecisionResponseDto parsed, List<CandidateSlotDto> candidates) {
        boolean isValidSlotId = candidates.stream()
                .anyMatch(candidate -> candidate.slotId().equals(parsed.recommendedSlotId()));

        if (isValidSlotId) {
            return parsed;
        }

        String fallbackSlotId = resolveFallbackCandidate(candidates).slotId();
        log.warn("Gemini가 candidates에 없는 recommendedSlotId({})를 응답 → sortType 순서가 아닌 " +
                        "우선순위 기반 fallback 후보({})로 대체합니다.",
                parsed.recommendedSlotId(), fallbackSlotId);

        return LLMDecisionResponseDto.recommendation(
                fallbackSlotId,
                parsed.reason(),
                parsed.alternativeNote(),
                parsed.fallbackUsed()
        );
    }

    // 프롬프트와 동일한 우선순위(가용인원 → 인접일정 촉박도 → 긴 시간 → 이른 시간)로 정렬한 1순위 후보
    // package-private: DecisionHelperServiceTest에서 정렬 우선순위를 직접 검증하기 위해 접근 허용
    CandidateSlotDto resolveFallbackCandidate(List<CandidateSlotDto> candidates) {
        return candidates.stream()
                .min(buildFallbackComparator())
                .orElseThrow(() -> new CustomException(ErrorCode.NO_CANDIDATE_AVAILABLE));
    }

    private Comparator<CandidateSlotDto> buildFallbackComparator() {
        return Comparator
                .comparing(CandidateSlotDto::availableMemberCount, Comparator.reverseOrder())
                .thenComparing(this::countTightSchedules)
                .thenComparing(CandidateSlotDto::durationMinutes, Comparator.reverseOrder())
                .thenComparing(CandidateSlotDto::startTime);
    }

    /**
     * LLM처럼 일정 제목의 맥락까지 판단할 수는 없으므로, gapMinutes가 임계값(30분) 이하인
     * beforeSlot/afterSlot을 가진 멤버 수를 세어 "촉박한 멤버 수"의 근사치로 사용한다.
     * beforeSlot/afterSlot 둘 다 촉박하더라도 같은 멤버면 1명으로만 집계한다(이름 기준 dedupe).
     * (unavailable 멤버는 애초에 beforeSlot/afterSlot이 항상 null이라 자연히 제외된다)
     */
    private int countTightSchedules(CandidateSlotDto candidate) {
        Set<String> tightMembers = new HashSet<>();

        for (MemberSummaryDto member : candidate.availableMembers()) {
            boolean beforeTight = member.beforeSlot() != null
                    && member.beforeSlot().gapMinutes() <= TIGHT_SCHEDULE_THRESHOLD_MINUTES;
            boolean afterTight = member.afterSlot() != null
                    && member.afterSlot().gapMinutes() <= TIGHT_SCHEDULE_THRESHOLD_MINUTES;

            if (beforeTight || afterTight) {
                tightMembers.add(member.name());
            }
        }

        return tightMembers.size();
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
