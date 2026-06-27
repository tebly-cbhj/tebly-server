package com.example.teblyserver.promise.service;

import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.decision.dto.CandidateSlotDto;
import com.example.teblyserver.decision.dto.DecisionCacheDto;
import com.example.teblyserver.decision.dto.MemberAvailabilityDto;
import com.example.teblyserver.decision.dto.MemberSummaryDto;
import com.example.teblyserver.decision.service.DecisionCacheService;
import com.example.teblyserver.promise.dto.internal.BusyScheduleTimeRange;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendRequest;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendationSortType;
import com.example.teblyserver.promise.dto.response.PromiseRecommendationMemberResponse;
import com.example.teblyserver.promise.dto.response.PromiseTimeRecommendationResponse;
import com.example.teblyserver.room.domain.InviteStatus;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.room.domain.RoomMember;
import com.example.teblyserver.room.repository.RoomRepository;
import com.example.teblyserver.schedule.domain.RepeatType;
import com.example.teblyserver.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromiseRecommendationService {

    private static final int SLOT_MINUTES = 30;
    private static final int MAX_CANDIDATE_COUNT = 100;
    private static final int RESPONSE_RECOMMENDATION_COUNT = 5;

    private final RoomRepository roomRepository;
    private final ScheduleRepository scheduleRepository;
    private final DecisionCacheService decisionCacheService;

    public List<PromiseTimeRecommendationResponse> recommendPromiseTimes(
            Long userId,
            Long roomId,
            PromiseTimeRecommendRequest request
    ) {
        LocalDateTime now = LocalDateTime.now();

        // request DTO 검증
        validateRequest(request);

        // 방 찾고 검증
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // 추천은 실제로 방에 참여 중인 멤버 기준
        // 방에 들어온 멤버(ACCEPTED)를 기준(초대 대기 중인 멤버 x)
        List<RoomMember> acceptedMembers = room.getMembers().stream()
                .filter(member -> member.getInviteStatus() == InviteStatus.ACCEPTED)
                .toList();

        // 방에 속한 멤버가 '빈 시간 추천 API'를 호출한 것인지 검증
        // 방에 속하지 않은 사람이 추천 API를 호출하면 안됨
        boolean isLoginUserRoomMember = acceptedMembers.stream()
                .anyMatch(member -> member.getUser().getId().equals(userId));

        if (!isLoginUserRoomMember) {
            throw new CustomException(ErrorCode.ROOM_FORBIDDEN);
        }

        // 방 멤버 ID 리스트
        List<Long> memberIds = acceptedMembers.stream()
                .map(member -> member.getUser().getId())
                .toList();

        int totalMemberCount = memberIds.size(); // 방 멤버 수

        List<PromiseRecommendationMemberResponse> memberResponses = acceptedMembers.stream()
                .map(member -> new PromiseRecommendationMemberResponse(
                        member.getUser().getId(),
                        member.getUser().getNickname(),        // [주의] User 엔티티 필드명에 맞게 수정
                        member.getUser().getProfileImageUrl()  // [주의] 없으면 DTO에서도 제거
                ))
                .toList();

        // request로 넘어온 시작 날짜, 끝 날짜, 시간대를 바탕으로 LocalDateTime 만듦
        // TODO: 만약 searchStartTime과 searchEndTime이 request를 타고 넘어오는 것이 아니라면 helper 메서드를 만들어서 생성해줘야될듯
        LocalDateTime searchPeriodStart =
                request.proposeStartDate().atTime(request.searchStartTime());

        LocalDateTime searchPeriodEnd =
                request.proposeEndDate().atTime(request.searchEndTime());

        // 방 멤버들의 등록된 일정(바쁜일정)을 가져옴
        /*
            등록된 일정 = 바쁜 시간
            일정이 없는 시간 = 빈 시간
            모든 멤버가 일정이 없는 시간 = 추천 시간
         */
        List<BusyScheduleTimeRange> busySchedules =
                scheduleRepository.findBusySchedulesByUserIdsAndPeriod(
                        memberIds,
                        searchPeriodStart,
                        searchPeriodEnd
                );

        // 1. 전원 가능 후보 먼저 수집
        List<PromiseTimeRecommendationResponse> allAvailableCandidates =
                collectAllAvailableCandidates(
                        request,
                        memberIds,
                        busySchedules,
                        totalMemberCount,
                        now,
                        memberResponses
                );

// 2. 최종 정렬에 사용할 후보군
        List<PromiseTimeRecommendationResponse> candidatePool = new ArrayList<>();

// 3. 전원 가능 후보를 먼저 후보군에 넣는다.
//    단, MAX_CANDIDATE_COUNT를 넘기지 않도록 제한한다.
        for (PromiseTimeRecommendationResponse candidate : allAvailableCandidates) {
            if (candidatePool.size() >= MAX_CANDIDATE_COUNT) {
                break;
            }

            candidatePool.add(candidate);
        }

// 4. 전원 가능 후보가 전혀 없다면 충돌 최소 후보를 수집하여 추가한다.
        if (candidatePool.size() < MAX_CANDIDATE_COUNT) {
            List<PromiseTimeRecommendationResponse> leastConflictCandidates =
                    collectLeastConflictCandidates(
                            request,
                            memberIds,
                            busySchedules,
                            totalMemberCount,
                            now,
                            memberResponses
                    );

            for (PromiseTimeRecommendationResponse candidate : leastConflictCandidates) {
                if (candidatePool.size() >= MAX_CANDIDATE_COUNT) {
                    break;
                }

                // 전원 가능 후보는 제외
                if (candidate.allAvailable()) {
                    continue;
                }

                // 같은 startTime/endTime이 이미 있으면 중복 제거
                if (isDuplicateTimeRange(candidate, candidatePool)) {
                    continue;
                }

                candidatePool.add(candidate);
            }
        }

// 5. 전원 가능 후보와 충돌 최소 후보를 함께 정렬한 뒤 최종 5개 반환
        List<PromiseTimeRecommendationResponse> finalRecommendations = candidatePool.stream()
                .sorted(getFinalRecommendationComparator(request.sortType()))
                .limit(RESPONSE_RECOMMENDATION_COUNT)
                .toList();

        // 6. 알고리즘 결과를 Redis에 캐싱 → 결정 도우미(LLM) API에서 roomId로 꺼내 사용
        cacheDecisionResult(roomId, request, finalRecommendations);

        return finalRecommendations;
    }

    // 추천 결과를 결정 도우미용 캐시(DecisionCacheDto)로 변환해 Redis에 저장
    private void cacheDecisionResult(
            Long roomId,
            PromiseTimeRecommendRequest request,
            List<PromiseTimeRecommendationResponse> finalRecommendations
    ) {
        boolean noCandidate = finalRecommendations.isEmpty();

        List<CandidateSlotDto> candidates = toCandidateSlots(finalRecommendations);

        // TODO: noCandidate=true 시 memberAvailability 구성 필요
        //       (현재 알고리즘에서 멤버별 freeRanges 직접 추출이 어려워 일단 빈 리스트로 저장)
        List<MemberAvailabilityDto> memberAvailability = noCandidate ? List.of() : null;

        DecisionCacheDto cacheDto = new DecisionCacheDto(
                noCandidate,
                candidates,
                memberAvailability,
                request.proposeStartDate(),
                request.proposeEndDate()
        );

        decisionCacheService.saveDecisionCache(roomId, cacheDto);
    }

    // PromiseTimeRecommendationResponse → CandidateSlotDto 변환 (slotId는 순서대로 "slot-N" 부여)
    private List<CandidateSlotDto> toCandidateSlots(
            List<PromiseTimeRecommendationResponse> recommendations
    ) {
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
                            toMemberSummaries(recommendation.availableMembers()),
                            toMemberSummaries(recommendation.unavailableMembers())
                    )
            );
        }

        return candidateSlots;
    }

    private List<MemberSummaryDto> toMemberSummaries(
            List<PromiseRecommendationMemberResponse> members
    ) {
        return members.stream()
                .map(member -> new MemberSummaryDto(member.nickname()))
                .toList();
    }


    // 전체 추천 기간에서 "멤버 전원 가능 후보"를 수집하는 메서드
    private List<PromiseTimeRecommendationResponse> collectAllAvailableCandidates(
            PromiseTimeRecommendRequest request,
            List<Long> memberIds,
            List<BusyScheduleTimeRange> busySchedules,
            int totalMemberCount,
            LocalDateTime now,
            List<PromiseRecommendationMemberResponse> memberResponses
    ) {
        List<PromiseTimeRecommendationResponse> candidates = new ArrayList<>();

        LocalDate currentDate = request.proposeStartDate();

        while (!currentDate.isAfter(request.proposeEndDate())) {
            candidates.addAll(
                    findDailyRecommendations(
                            currentDate,
                            request,
                            memberIds,
                            busySchedules,
                            totalMemberCount,
                            now,
                            memberResponses
                    )
            );

            currentDate = currentDate.plusDays(1);
        }

        return candidates;
    }

    // 전체 추천 기간에서 "충돌 최소 후보"를 수집하는 메서드
    private List<PromiseTimeRecommendationResponse> collectLeastConflictCandidates(
            PromiseTimeRecommendRequest request,
            List<Long> memberIds,
            List<BusyScheduleTimeRange> busySchedules,
            int totalMemberCount,
            LocalDateTime now,
            List<PromiseRecommendationMemberResponse> memberResponses
    ) {
        List<PromiseTimeRecommendationResponse> candidates = new ArrayList<>();

        LocalDate currentDate = request.proposeStartDate();

        while (!currentDate.isAfter(request.proposeEndDate())) {
            candidates.addAll(
                    findDailyLeastConflictRecommendations(
                            currentDate,
                            request,
                            memberIds,
                            busySchedules,
                            totalMemberCount,
                            now,
                            memberResponses
                    )
            );

            currentDate = currentDate.plusDays(1);
        }

        return candidates;
    }



    private List<PromiseTimeRecommendationResponse> findDailyRecommendations(
            LocalDate date,
            PromiseTimeRecommendRequest request,
            List<Long> memberIds,
            List<BusyScheduleTimeRange> busySchedules,
            int totalMemberCount,
            LocalDateTime now,
            List<PromiseRecommendationMemberResponse> memberResponses
    ) {
        // 과거 날짜면 추천하지 않음
        if (date.isBefore(now.toLocalDate())) {
            return List.of();
        }

        // 시간대 설정
        LocalDateTime daySearchStart = date.atTime(request.searchStartTime());
        LocalDateTime daySearchEnd = date.atTime(request.searchEndTime());

        // 오늘이면 현재 시간 이전은 추천하지 않음
        if (date.isEqual(now.toLocalDate())) {
            // 현재 시간을 다음 30분 단위로 올림 처리
            LocalDateTime roundedNow = roundToNextSlot(now);

            if (roundedNow.isAfter(daySearchStart)) {
                daySearchStart = roundedNow;
            }
        }

        // 현재 시간이 이미 오늘 탐색 종료 시간 이후라면 오늘은 추천 불가
        if (!daySearchEnd.isAfter(daySearchStart)) {
            return List.of();
        }

        long searchWindowMinutes = Duration.between(daySearchStart, daySearchEnd).toMinutes(); // 분(minute)로 환산
        int blockCount = (int) (searchWindowMinutes / SLOT_MINUTES); // slot 개수 구하기

        boolean[][] busy = new boolean[totalMemberCount][blockCount];

        // 실제 userId랑 busy 배열에서의 index랑 매핑 시키기 위한 Map 만들기
        Map<Long, Integer> memberIndexMap = createMemberIndexMap(memberIds);

        // 추가: 반복 일정을 이 날짜 기준으로 실제 발생 일정처럼 펼침
        List<BusyScheduleTimeRange> expandedBusySchedules =
                expandBusySchedulesForDate(
                        busySchedules,
                        date,
                        daySearchStart,
                        daySearchEnd
                );

        // 멤버들의 실제 일정을 보고,
        // 단위 시간표 배열에서 바쁜 칸을 true로 표시
        markBusyBlocks(
                busy,
                memberIndexMap,
                expandedBusySchedules,
                daySearchStart,
                daySearchEnd,
                blockCount
        );

        // 약속시간(minDuration)을 만족하려면 필요한 블럭의 개수
        // ex) minduration = 120, SLOT_MINUTES = 30 -> 필요한 블럭 개수 = 4
        int requiredBlockCount = ceilDiv(request.minDuration(), SLOT_MINUTES);

        /*
            busy 배열을 처음부터 끝까지 훑으면서
            멤버 전원이 비어 있는 시간이 연속으로 이어지는 구간을 찾아
            추천 결과 리스트로 바꿈
        */
        return collectAllFreeTimeRanges(
                busy,
                daySearchStart,
                blockCount,
                requiredBlockCount,
                totalMemberCount,
                memberResponses
        );
    }

    // “전원 가능한 시간”이 없을 때 사용할
    // 충돌 최소 추천 후보를 만드는 메서드
    // findDailyRecommendations와 거의 유사 - 마지막 return 호출 메서드만 다름
    private List<PromiseTimeRecommendationResponse> findDailyLeastConflictRecommendations(
            LocalDate date,
            PromiseTimeRecommendRequest request,
            List<Long> memberIds,
            List<BusyScheduleTimeRange> busySchedules,
            int totalMemberCount,
            LocalDateTime now,
            List<PromiseRecommendationMemberResponse> memberResponses
    ) {
        if (date.isBefore(now.toLocalDate())) {
            return List.of();
        }

        LocalDateTime daySearchStart = date.atTime(request.searchStartTime());
        LocalDateTime daySearchEnd = date.atTime(request.searchEndTime());

        if (date.isEqual(now.toLocalDate())) {
            LocalDateTime roundedNow = roundToNextSlot(now);

            if (roundedNow.isAfter(daySearchStart)) {
                daySearchStart = roundedNow;
            }
        }

        if (!daySearchEnd.isAfter(daySearchStart)) {
            return List.of();
        }

        long searchWindowMinutes = Duration.between(daySearchStart, daySearchEnd).toMinutes();
        int blockCount = (int) (searchWindowMinutes / SLOT_MINUTES);

        int requiredBlockCount = ceilDiv(request.minDuration(), SLOT_MINUTES);

        if (blockCount < requiredBlockCount) {
            return List.of();
        }

        boolean[][] busy = new boolean[totalMemberCount][blockCount];

        Map<Long, Integer> memberIndexMap = createMemberIndexMap(memberIds);

        List<BusyScheduleTimeRange> expandedBusySchedules =
                expandBusySchedulesForDate(
                        busySchedules,
                        date,
                        daySearchStart,
                        daySearchEnd
                );

        markBusyBlocks(
                busy,
                memberIndexMap,
                expandedBusySchedules,
                daySearchStart,
                daySearchEnd,
                blockCount
        );

        return collectLeastConflictTimeRanges(
                busy,
                daySearchStart,
                blockCount,
                requiredBlockCount,
                totalMemberCount,
                memberResponses
        );
    }


    // 현재 시간을 다음 30분 단위로 올림 처리
    // 예를 들어 지금이 18:07이면 18:30부터 추천
    private LocalDateTime roundToNextSlot(LocalDateTime time) {
        LocalDateTime truncated = time.withSecond(0).withNano(0);

        int minute = truncated.getMinute();
        int remainder = minute % SLOT_MINUTES;

        if (remainder == 0 && time.getSecond() == 0 && time.getNano() == 0) {
            return truncated;
        }

        int minutesToAdd = (remainder == 0)
                ? SLOT_MINUTES
                : SLOT_MINUTES - remainder;

        return truncated.plusMinutes(minutesToAdd);
    }


    // DB에서 가져온 원본 일정을 보고, 추천 날짜에 실제로 발생하는 일정으로 바꿔주는 역할
    /*
        예를 들어 원본 반복 일정이
            userId = 3
            startTime = 2026-03-05 10:00
            endTime = 2026-03-05 12:00
            repeatType = WEEKLY

        추천 날짜가 2026-06-25이고, 이 날도 목요일이면 가상 일정으로 바뀐다.

            userId = 3
            startTime = 2026-06-25 10:00
            endTime = 2026-06-25 12:00
            repeatType = WEEKLY

         이 가상 일정이 busy 배열에 표시
     */
    private List<BusyScheduleTimeRange> expandBusySchedulesForDate(
            List<BusyScheduleTimeRange> originalSchedules,
            LocalDate date,
            LocalDateTime daySearchStart,
            LocalDateTime daySearchEnd
    ) {
        List<BusyScheduleTimeRange> expandedSchedules = new ArrayList<>();

        for (BusyScheduleTimeRange schedule : originalSchedules) {
            if (schedule.repeatType() == RepeatType.NONE) {
                if (isOverlapping(schedule.startTime(), schedule.endTime(), daySearchStart, daySearchEnd)) {
                    expandedSchedules.add(schedule);
                }
                continue;
            }

            // 기본적으로 해당 날짜에 발생하는 반복 일정을 추가
            addRepeatedOccurrenceIfOverlaps(
                    expandedSchedules,
                    schedule,
                    date,
                    daySearchStart,
                    daySearchEnd
            );

            // 자정을 넘는 일정까지 고려하고 싶을 때 필요
            // 예: 매주 월요일 23:00~화요일 01:00
            addRepeatedOccurrenceIfOverlaps(
                    expandedSchedules,
                    schedule,
                    date.minusDays(1),
                    daySearchStart,
                    daySearchEnd
            );
        }

        return expandedSchedules;
    }

    // 반복 일정 하나를 실제 발생 일정으로 추가하는 메서드
    private void addRepeatedOccurrenceIfOverlaps(
            List<BusyScheduleTimeRange> expandedSchedules,
            BusyScheduleTimeRange schedule,
            LocalDate occurrenceDate,
            LocalDateTime daySearchStart,
            LocalDateTime daySearchEnd
    ) {
        if (!isOccurrenceDate(schedule, occurrenceDate)) {
            return;
        }

        Duration duration = Duration.between(schedule.startTime(), schedule.endTime());

        LocalDateTime occurrenceStart =
                occurrenceDate.atTime(schedule.startTime().toLocalTime());

        LocalDateTime occurrenceEnd =
                occurrenceStart.plus(duration);

        if (!isOverlapping(occurrenceStart, occurrenceEnd, daySearchStart, daySearchEnd)) {
            return;
        }

        expandedSchedules.add(
                new BusyScheduleTimeRange(
                        schedule.userId(),
                        occurrenceStart,
                        occurrenceEnd,
                        schedule.repeatType()
                )
        );
    }

    // 해당 날짜에 반복 일정이 발생하는지 판단
    private boolean isOccurrenceDate(
            BusyScheduleTimeRange schedule,
            LocalDate occurrenceDate
    ) {
        LocalDate originalDate = schedule.startTime().toLocalDate();

        if (occurrenceDate.isBefore(originalDate)) {
            return false;
        }

        return switch (schedule.repeatType()) {
            case DAILY -> true;

            case WEEKLY ->
                    occurrenceDate.getDayOfWeek() == originalDate.getDayOfWeek();

            case MONTHLY ->
                    occurrenceDate.getDayOfMonth() == originalDate.getDayOfMonth();

            case NONE -> occurrenceDate.isEqual(originalDate);
        };
    }

    // 겹치는지 확인하는 메서드
    private boolean isOverlapping(
            LocalDateTime startA,
            LocalDateTime endA,
            LocalDateTime startB,
            LocalDateTime endB
    ) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    // 실제 userId랑 busy 배열에서의 index랑 매핑 시키기 위한 Map 만들기
    private Map<Long, Integer> createMemberIndexMap(List<Long> memberIds) {
        Map<Long, Integer> memberIndexMap = new HashMap<>();

        for (int i = 0; i < memberIds.size(); i++) {
            memberIndexMap.put(memberIds.get(i), i);
        }

        return memberIndexMap;
    }

    // DB에서 가져온 일정들을 알고리즘이 쓰기 좋은 boolean 배열 형태로 바꿔주는 메서드
    private void markBusyBlocks(
            boolean[][] busy,
            Map<Long, Integer> memberIndexMap,
            List<BusyScheduleTimeRange> busySchedules,
            LocalDateTime daySearchStart,
            LocalDateTime daySearchEnd,
            int blockCount
    ) {
        // 모든 유저들의 일정을 돌면서 busyBlock에 count를 늘림
        for (BusyScheduleTimeRange schedule : busySchedules) {
            Integer memberIndex = memberIndexMap.get(schedule.userId());

            if (memberIndex == null) {
                continue;
            }

            // schedule중 추천 시간대를 벗어난 시간은 필요없음
            // 실제로 겹치는 부분만 잘라내는 코드
            LocalDateTime overlapStart = max(schedule.startTime(), daySearchStart);
            LocalDateTime overlapEnd = min(schedule.endTime(), daySearchEnd);

            if (!overlapEnd.isAfter(overlapStart)) {
                continue;
            }

            // 탐색 시작 시간으로부터 몇 분 뒤에 일정이 시작/끝나는지 계산하는 코드
            /*
                예를 들어 오늘 탐색 시간이
                    daySearchStart = 09:00
                    daySearchEnd   = 22:00

                    overlapStart = 10:00
                    overlapEnd   = 11:30

                startOffsetMinutes = 60 -> 일정이 탐색 시작 시간인 09:00으로부터 60분 뒤에 시작한다
                endOffsetMinutes = 150 -> 일정이 탐색 시작 시간인 09:00으로부터 150분 뒤에 끝난다
             */
            long startOffsetMinutes = Duration.between(daySearchStart, overlapStart).toMinutes();
            long endOffsetMinutes = Duration.between(daySearchStart, overlapEnd).toMinutes();

            // 위에서 구한 startOffsetMinutes, endOffsetMinutes를 가지고 busy block으로 칠할 범위를 구함
            /*
                startOffsetMinutes -> 60 / 30 = 2
                endOffsetMinutes ->   150 / 30 = 5
                이 일정은 배열에서 2번 블록부터 5번 블록 직전까지 칠한다
             */
            int startBlockIndex = (int) (startOffsetMinutes / SLOT_MINUTES);
            int endBlockIndexExclusive = ceilDiv(endOffsetMinutes, SLOT_MINUTES);

            startBlockIndex = Math.max(0, startBlockIndex);
            endBlockIndexExclusive = Math.min(blockCount, endBlockIndexExclusive);

            for (int block = startBlockIndex; block < endBlockIndexExclusive; block++) {
                busy[memberIndex][block] = true;
            }
        }
    }


    /*
        busy 배열을 처음부터 끝까지 훑으면서
        멤버 전원이 비어 있는 시간이 연속으로 이어지는 구간을 찾아
        추천 결과 리스트로 바꾸는 메서드
     */
    private List<PromiseTimeRecommendationResponse> collectAllFreeTimeRanges(
            boolean[][] busy,
            LocalDateTime daySearchStart,
            int blockCount,
            int requiredBlockCount,
            int totalMemberCount,
            List<PromiseRecommendationMemberResponse> memberResponses
    ) {
        List<PromiseTimeRecommendationResponse> recommendations = new ArrayList<>();

        // 핵심 변수
        int runStartBlock = -1; // 연속으로 비어 있는 구간이 몇 번 블록에서 시작됐는지
        int runLength = 0; // 그 빈 구간이 몇 칸 연속으로 이어지고 있는지

        // <= blockCount 로 처리한 이유는 마지막에 이어지고 있던 빈 구간을 마무리 처리하기 위함
        for (int block = 0; block <= blockCount; block++) {
            boolean isInsideBlockRange = block < blockCount;
            boolean isAllFree = isInsideBlockRange && isAllMembersFreeAtBlock(busy, block, totalMemberCount);

            if (isAllFree) {
                if (runStartBlock == -1) {
                    runStartBlock = block;
                }

                runLength++;
                continue;
            }

            // '현재 블록'이 모두 가능한 시간이 아닌 경우
            if (runStartBlock != -1) { // runStartBlock != -1이면 방금 전까지 빈 구간이 이어지고 있었다는 뜻
                // 연속 빈 구간이 최소 시간 이상인지 확인
                // 최소 시간 이상이라면 block을 시간으로 변경해서 DTO로 만들어 recommendations에 추가
                if (runLength >= requiredBlockCount) {
                    LocalDateTime startTime =
                            daySearchStart.plusMinutes((long) runStartBlock * SLOT_MINUTES);

                    LocalDateTime endTime =
                            daySearchStart.plusMinutes((long) (runStartBlock + runLength) * SLOT_MINUTES);

                    int durationMinutes = (int) Duration.between(startTime, endTime).toMinutes();

                    recommendations.add(
                            new PromiseTimeRecommendationResponse(
                                    startTime,
                                    endTime,
                                    durationMinutes,
                                    totalMemberCount,
                                    totalMemberCount,
                                    true,
                                    "멤버 전원 가능한 시간",
                                    memberResponses, // [추가] 전원 가능이므로 전체 멤버
                                    List.of()
                            )
                    );
                }

                runStartBlock = -1;
                runLength = 0;
            }
        }

        return recommendations;
    }

    // 모든 멤버가 가능한 block인지
    private boolean isAllMembersFreeAtBlock(
            boolean[][] busy,
            int block, // 이 블럭에 모든 멤버가 가능?
            int totalMemberCount
    ) {
        for (int member = 0; member < totalMemberCount; member++) {
            if (busy[member][block]) {
                return false;
            }
        }

        return true;
    }


    /*
        모든 멤버가 가능한 시간이 없을 때,
        minDuration 길이만큼 시간창을 30분씩 밀어보면서
        가장 많은 멤버가 참석 가능한 시간대를 후보로 만드는 메서드
     */
    /*
    [수정]
    기존 방식:
        minDuration 길이만큼만 충돌 최소 후보를 반환했음.
        예: 14:00~16:00 / 2명 가능

    수정 방식:
        1. 먼저 minDuration 구간에서 가능한 멤버 집합을 구함
        2. 그 멤버들이 계속 가능한 만큼 오른쪽으로 구간을 확장함
        3. 더 큰 후보에 포함되는 작은 후보는 제거함

    예:
        A, D가 14:00~17:00 전체 가능하고 minDuration이 2시간이면

        기존:
            14:00~16:00 / 2명 가능
            14:30~16:30 / 2명 가능
            15:00~17:00 / 2명 가능

        수정 후:
            14:00~17:00 / 2명 가능
*/
    private List<PromiseTimeRecommendationResponse> collectLeastConflictTimeRanges(
            boolean[][] busy,
            LocalDateTime daySearchStart,
            int blockCount,
            int requiredBlockCount,
            int totalMemberCount,
            List<PromiseRecommendationMemberResponse> memberResponses
    ) {
        // 가능한 멤버 집합까지 들고 있는 내부 후보를 먼저 만든다.
        List<LeastConflictCandidate> rawCandidates = new ArrayList<>();

        for (int startBlock = 0; startBlock <= blockCount - requiredBlockCount; startBlock++) {

        /*
            startBlock부터 minDuration 길이만큼 봤을 때,
            그 구간 전체에 참석 가능한 멤버들의 index를 구한다.

            예:
                14:00~16:00 기준
                A 가능, B 불가능, C 불가능, D 가능

                availableMembers = [A, D]
        */
            Set<Integer> availableMembers = getAvailableMemberIndexesForRange(
                    busy,
                    startBlock,
                    requiredBlockCount,
                    totalMemberCount
            );

            if (availableMembers.isEmpty()) {
                continue;
            }

        /*
            처음에는 최소 길이만큼만 후보 구간을 잡는다.

            예:
                startBlock = 14:00
                requiredBlockCount = 4칸
                candidateEndBlockExclusive = 16:00
        */
            int candidateEndBlockExclusive = startBlock + requiredBlockCount;

        /*
            minDuration 구간에서 가능했던 멤버들이
            다음 블록에서도 계속 가능하면 후보 구간을 확장한다.

            예:
                availableMembers = [A, D]

                16:00~16:30에도 A, D 가능하면 확장
                16:30~17:00에도 A, D 가능하면 확장

                최종 후보: 14:00~17:00
        */
            while (candidateEndBlockExclusive < blockCount
                    && areMembersFreeAtBlock(busy, availableMembers, candidateEndBlockExclusive)) {
                candidateEndBlockExclusive++;
            }

            LocalDateTime startTime =
                    daySearchStart.plusMinutes((long) startBlock * SLOT_MINUTES);

            LocalDateTime endTime =
                    daySearchStart.plusMinutes((long) candidateEndBlockExclusive * SLOT_MINUTES);

            int durationMinutes =
                    (int) Duration.between(startTime, endTime).toMinutes();

            rawCandidates.add(
                    new LeastConflictCandidate(
                            startTime,
                            endTime,
                            durationMinutes,
                            Set.copyOf(availableMembers),
                            totalMemberCount
                    )
            );
        }
        /*
        [추가]
        더 큰 후보에 포함되는 작은 후보 제거.

        예:
            14:00~17:00 / A,D 가능
            14:30~17:00 / A,D 가능
            15:00~17:00 / A,D 가능

        이 경우 뒤의 두 개는 14:00~17:00 안에 포함되므로 제거.
    */
        List<LeastConflictCandidate> filteredCandidates =
                removeDominatedLeastConflictCandidates(rawCandidates);

        // [수정] 내부 후보를 최종 응답 DTO로 변환
        return filteredCandidates.stream()
                .map(candidate -> candidate.toResponse(memberResponses))
                .toList();
    }


    /*
    특정 구간 전체에 참석 가능한 멤버들의 index를 구하는 메서드.

    기존 countAvailableMembersForRange()는 "몇 명 가능한지"만 반환했지만,
    이제는 후보 포함 관계를 판단하기 위해 "누가 가능한지"도 필요하다.
*/
    private Set<Integer> getAvailableMemberIndexesForRange(
            boolean[][] busy,
            int startBlock,
            int requiredBlockCount,
            int totalMemberCount
    ) {
        Set<Integer> availableMembers = new HashSet<>();

        for (int member = 0; member < totalMemberCount; member++) {
            if (isMemberFreeForRange(busy, member, startBlock, requiredBlockCount)) {
                availableMembers.add(member);
            }
        }

        return availableMembers;
    }

    /*
        이미 가능한 멤버 집합이 다음 block에서도 모두 가능한지 확인하는 메서드.

        예:
            availableMembers = [A, D]
            block = 16:00~16:30

            A가 이 블록에 바쁘면 false
            D가 이 블록에 바쁘면 false
            둘 다 안 바쁘면 true

        true면 후보 구간을 오른쪽으로 확장할 수 있다.
    */
    private boolean areMembersFreeAtBlock(
            boolean[][] busy,
            Set<Integer> members,
            int block
    ) {
        for (Integer member : members) {
            if (busy[member][block]) {
                return false;
            }
        }

        return true;
    }


    // 이 멤버가 이 추천 구간 전체에서 비어 있는가?
    private int countAvailableMembersForRange(
            boolean[][] busy,
            int startBlock,
            int requiredBlockCount,
            int totalMemberCount
    ) {
        int availableMemberCount = 0;

        for (int member = 0; member < totalMemberCount; member++) {
            if (isMemberFreeForRange(busy, member, startBlock, requiredBlockCount)) {
                availableMemberCount++;
            }
        }

        return availableMemberCount;
    }

    // 한 멤버가 구간 전체에서 가능한지 확인
    private boolean isMemberFreeForRange(
            boolean[][] busy,
            int member,
            int startBlock,
            int requiredBlockCount
    ) {
        for (int block = startBlock; block < startBlock + requiredBlockCount; block++) {
            if (busy[member][block]) {
                return false;
            }
        }

        return true;
    }


    /*
    더 큰 후보에 포함되는 작은 충돌 최소 후보를 제거
    제거 기준:
        1. other가 candidate의 시간 범위를 완전히 포함하고
        2. other의 가능 멤버 집합이 candidate의 가능 멤버 집합을 모두 포함하고
        3. other가 candidate보다 실제로 더 넓거나 더 많은 멤버를 포함하면
    candidate는 제거 가능
    예:
        other     = 14:00~17:00 / A,D 가능
        candidate = 14:30~17:00 / A,D 가능

        other가 candidate를 포함하고,
        가능한 멤버도 같으므로 candidate 제거.
    */
    private List<LeastConflictCandidate> removeDominatedLeastConflictCandidates(
            List<LeastConflictCandidate> candidates
    ) {
        List<LeastConflictCandidate> result = new ArrayList<>();

        for (LeastConflictCandidate candidate : candidates) {
            boolean dominated = candidates.stream()
                    .anyMatch(other ->
                            other != candidate
                                    && containsTimeRange(other, candidate)
                                    && other.availableMembers().containsAll(candidate.availableMembers())
                                    && isStrictlyBetterOrWider(other, candidate)
                    );

            if (!dominated) {
                result.add(candidate);
            }
        }

        return result;
    }

    /*
        outer가 inner의 시간 범위를 완전히 포함하는지 확인한다.
        예:
            outer = 14:00~17:00
            inner = 14:30~17:00
            outer.startTime <= inner.startTime
            outer.endTime >= inner.endTime
            따라서 true.
    */
    private boolean containsTimeRange(
            LeastConflictCandidate outer,
            LeastConflictCandidate inner
    ) {
        return !outer.startTime().isAfter(inner.startTime())
                && !outer.endTime().isBefore(inner.endTime());
    }

    /*
        완전히 같은 후보끼리 서로 제거되는 것을 막기 위한 메서드.
        other가 candidate보다
            - 더 일찍 시작하거나
            - 더 늦게 끝나거나

        other가 더 낫거나 더 넓은 후보라고 판단한다.
    */
    private boolean isStrictlyBetterOrWider(
            LeastConflictCandidate other,
            LeastConflictCandidate candidate
    ) {
        boolean startsEarlier = other.startTime().isBefore(candidate.startTime());
        boolean endsLater = other.endTime().isAfter(candidate.endTime());
        boolean hasMoreAvailableMembers =
                other.availableMemberCount() > candidate.availableMemberCount();

        return startsEarlier || endsLater || hasMoreAvailableMembers;
    }

    // 추천 reason 메서드
    private String buildRecommendationReason(
            int availableMemberCount,
            int totalMemberCount
    ) {
        if (availableMemberCount == totalMemberCount) {
            return "멤버 전원 가능한 시간";
        }

        return "일정 충돌이 가장 적은 시간";
    }


    // 같은 시간대가 후보군에 중복으로 들어가는 것을 막는 메서드
    private boolean isDuplicateTimeRange(
            PromiseTimeRecommendationResponse candidate,
            List<PromiseTimeRecommendationResponse> existingCandidates
    ) {
        return existingCandidates.stream()
                .anyMatch(existing ->
                        existing.startTime().equals(candidate.startTime())
                                && existing.endTime().equals(candidate.endTime())
                );
    }


// 추천 후보군에는 전원 가능 후보와 충돌 최소 후보가 섞여 있을 수 있다.
// 따라서 정렬 시 availableMemberCount도 보조 기준으로 넣어준다.
    private Comparator<PromiseTimeRecommendationResponse> getFinalRecommendationComparator(
            PromiseTimeRecommendationSortType sortType
    ) {
        PromiseTimeRecommendationSortType effectiveSortType =
                sortType == null ? PromiseTimeRecommendationSortType.EARLIEST : sortType;

        return switch (effectiveSortType) {
            // 빠른 시간순
            // 시간이 같으면 가능한 멤버 수가 많은 후보 우선
            // 그래도 같으면 더 긴 후보 우선
            case EARLIEST -> Comparator
                    .comparing(PromiseTimeRecommendationResponse::startTime)
                    .thenComparing(
                            PromiseTimeRecommendationResponse::availableMemberCount,
                            Comparator.reverseOrder()
                    )
                    .thenComparing(
                            PromiseTimeRecommendationResponse::durationMinutes,
                            Comparator.reverseOrder()
                    );

            // 늦은 시간순
            // 사용자가 늦은 시간을 선호하면, 충돌이 조금 있는 후보도 위로 올라올 수 있음
            case LATEST -> Comparator
                    .comparing(PromiseTimeRecommendationResponse::startTime)
                    .reversed()
                    .thenComparing(
                            PromiseTimeRecommendationResponse::availableMemberCount,
                            Comparator.reverseOrder()
                    )
                    .thenComparing(
                            PromiseTimeRecommendationResponse::durationMinutes,
                            Comparator.reverseOrder()
                    );

            // 긴 시간순
            // 긴 가능 구간을 먼저 보여주고,
            // 길이가 같으면 가능한 멤버 수가 많은 후보 우선
            // 그래도 같으면 빠른 시간순
            case LONGEST -> Comparator
                    .comparing(
                            PromiseTimeRecommendationResponse::durationMinutes,
                            Comparator.reverseOrder()
                    )
                    .thenComparing(
                            PromiseTimeRecommendationResponse::availableMemberCount,
                            Comparator.reverseOrder()
                    )
                    .thenComparing(PromiseTimeRecommendationResponse::startTime);
        };
    }


    // requset DTO 검증 메서드
    private void validateRequest(PromiseTimeRecommendRequest request) {
        if (request.proposeEndDate().isBefore(request.proposeStartDate())) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        if (!request.searchEndTime().isAfter(request.searchStartTime())) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        long searchWindowMinutes =
                Duration.between(request.searchStartTime(), request.searchEndTime()).toMinutes();

        if (searchWindowMinutes < request.minDuration()) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_DURATION);
        }

        if (searchWindowMinutes % SLOT_MINUTES != 0) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        if (request.searchStartTime().getMinute() % SLOT_MINUTES != 0
                || request.searchEndTime().getMinute() % SLOT_MINUTES != 0
                || request.searchStartTime().getSecond() != 0
                || request.searchStartTime().getNano() != 0
                || request.searchEndTime().getSecond() != 0
                || request.searchEndTime().getNano() != 0) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }
    }

    private LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private int ceilDiv(long value, int divisor) {
        return (int) ((value + divisor - 1) / divisor);
    }


    private record LeastConflictCandidate(
            LocalDateTime startTime,
            LocalDateTime endTime,
            int durationMinutes,
            Set<Integer> availableMembers,
            int totalMemberCount
    ) {
        int availableMemberCount() {
            return availableMembers.size();
        }

        // [수정] memberResponses를 파라미터로 받도록 변경
        PromiseTimeRecommendationResponse toResponse(
                List<PromiseRecommendationMemberResponse> memberResponses
        ) {
            List<PromiseRecommendationMemberResponse> availableMemberResponses = new ArrayList<>();
            List<PromiseRecommendationMemberResponse> unavailableMemberResponses = new ArrayList<>();

            // [추가]
            // availableMembers는 배열 index 집합이다.
            // 전체 memberResponses를 돌면서 index가 포함되어 있으면 가능,
            // 포함되어 있지 않으면 불가능으로 분리한다.
            for (int i = 0; i < memberResponses.size(); i++) {
                if (availableMembers.contains(i)) {
                    availableMemberResponses.add(memberResponses.get(i));
                } else {
                    unavailableMemberResponses.add(memberResponses.get(i));
                }
            }

            return new PromiseTimeRecommendationResponse(
                    startTime,
                    endTime,
                    durationMinutes,
                    availableMemberCount(),
                    totalMemberCount,
                    availableMemberCount() == totalMemberCount,
                    availableMemberCount() == totalMemberCount
                            ? "멤버 전원 가능한 시간"
                            : "일정 충돌이 가장 적은 시간",
                    availableMemberResponses,   // [추가]
                    unavailableMemberResponses  // [추가]
            );
        }
    }
}



