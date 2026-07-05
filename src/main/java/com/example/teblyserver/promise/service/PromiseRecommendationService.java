package com.example.teblyserver.promise.service;

import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.promise.domain.Promise;
import com.example.teblyserver.promise.domain.PromiseMember;
import com.example.teblyserver.promise.domain.PromiseStatus;
import com.example.teblyserver.promise.dto.internal.BusyScheduleTimeRange;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendRequest;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendationSortType;
import com.example.teblyserver.promise.dto.request.PromiseUpdateTimeRecommendRequest;
import com.example.teblyserver.promise.dto.response.PromiseRecommendationMemberResponse;
import com.example.teblyserver.promise.dto.response.PromiseTimeRecommendationResponse;
import com.example.teblyserver.promise.repository.PromiseRepository;
import com.example.teblyserver.room.domain.InviteStatus;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.room.domain.RoomMember;
import com.example.teblyserver.room.repository.RoomRepository;
import com.example.teblyserver.schedule.domain.RepeatType;
import com.example.teblyserver.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromiseRecommendationService {

    private static final int SLOT_MINUTES = 30;
    private static final int MAX_CANDIDATE_COUNT = 100;
    private static final int RESPONSE_RECOMMENDATION_COUNT = 5;

    private final RoomRepository roomRepository;
    private final ScheduleRepository scheduleRepository;
    private final PromiseRepository promiseRepository;

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

        List<RoomMember> selectedMembers = resolveSelectedAcceptedMembers(
                userId,
                acceptedMembers,
                request.selectedMemberIds()
        );

        // 방 멤버 ID 리스트
        List<Long> memberIds = selectedMembers.stream()
                .map(member -> member.getUser().getId())
                .toList();

        int totalMemberCount = memberIds.size(); // 방 멤버 수

        List<PromiseRecommendationMemberResponse> memberResponses = selectedMembers.stream()
                .map(member -> new PromiseRecommendationMemberResponse(
                        member.getUser().getId(),
                        member.getUser().getNickname(),
                        member.getUser().getProfileImageUrl()
                ))
                .toList();

        // request로 넘어온 시작 날짜, 끝 날짜, 시간대를 바탕으로 LocalDateTime 만듦
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
        return candidatePool.stream()
                .sorted(getFinalRecommendationComparator(request.sortType(),
                        now,
                        userId,
                        busySchedules))
                .limit(RESPONSE_RECOMMENDATION_COUNT)
                .toList();
    }

    /**
     * 결정 도우미(LLM)가 후보 슬롯 앞뒤로 촉박한 일정이 있는지 판단할 수 있도록,
     * 주어진 시간 윈도우 안에서 유저들의 일정을 조회해 반환한다.
     *
     * anchorDate는 반복 일정의 실제 발생 여부를 계산할 기준 날짜다.
     * (윈도우가 버퍼 때문에 전날/다음날로 살짝 걸치더라도, 판단 기준은 슬롯이 속한 날짜로 고정한다)
     */
    public List<BusyScheduleTimeRange> findExpandedSchedulesInWindow(
            List<Long> userIds,
            LocalDate anchorDate,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    ) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        List<BusyScheduleTimeRange> rawSchedules =
                scheduleRepository.findBusySchedulesByUserIdsAndPeriod(userIds, windowStart, windowEnd);

        return expandBusySchedulesForDate(rawSchedules, anchorDate, windowStart, windowEnd);
    }

    private List<RoomMember> resolveSelectedAcceptedMembers(
            Long loginUserId,
            List<RoomMember> acceptedMembers,
            List<Long> selectedMemberIds
    ) {
        if (selectedMemberIds == null || selectedMemberIds.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_MEMBER);
        }

        Map<Long, RoomMember> acceptedMemberMap = new HashMap<>();

        for (RoomMember member : acceptedMembers) {
            acceptedMemberMap.put(member.getUser().getId(), member);
        }

        // 로그인 유저가 방 ACCEPTED 멤버인지 확인
        if (!acceptedMemberMap.containsKey(loginUserId)) {
            throw new CustomException(ErrorCode.ROOM_FORBIDDEN);
        }

        Set<Long> participantIds = new LinkedHashSet<>();

        // 약속 생성자는 항상 포함
        participantIds.add(loginUserId);

        // 프론트에서 선택한 멤버들 추가
        participantIds.addAll(selectedMemberIds);

        return participantIds.stream()
                .map(memberId -> {
                    RoomMember roomMember = acceptedMemberMap.get(memberId);

                    if (roomMember == null) {
                        throw new CustomException(ErrorCode.INVALID_PROMISE_MEMBER);
                    }

                    return roomMember;
                })
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
                        schedule.title(),
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


    /**
        모든 멤버가 가능한 시간이 없을 때,
        minDuration 길이만큼 시간창을 30분씩 밀어보면서
        가장 많은 멤버가 참석 가능한 시간대를 후보로 만드는 메서드
     */
    /**
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

    /**
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


    /**
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

    /**
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

    /**
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
            PromiseTimeRecommendationSortType sortType,
            LocalDateTime now,
            Long hostId,
            List<BusyScheduleTimeRange> busySchedules
    ) {
        PromiseTimeRecommendationSortType effectiveSortType =
                sortType == null ? PromiseTimeRecommendationSortType.RECOMMENDED : sortType;

        return switch (effectiveSortType) {
            // 추천순
            // 1순위: 추천 점수 높은 순
            // 2순위: 참여 가능 멤버 수 많은 순
            // 3순위: 빠른 시간순
            // 4순위: 긴 시간순
            case RECOMMENDED -> Comparator
                    .comparing(
                            (PromiseTimeRecommendationResponse recommendation) ->
                                    calculateRecommendationScore(
                                            recommendation,
                                            now,
                                            hostId,
                                            busySchedules
                                    ),
                            Comparator.reverseOrder()
                    )
                    .thenComparing(
                            PromiseTimeRecommendationResponse::availableMemberCount,
                            Comparator.reverseOrder()
                    )
                    .thenComparing(PromiseTimeRecommendationResponse::startTime)
                    .thenComparing(
                            PromiseTimeRecommendationResponse::durationMinutes,
                            Comparator.reverseOrder()
                    );

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

    /**
     * 추천 점수 =
     * 참여도 점수 50점
     * + 시간대 선호도 20점
     * + 약속까지의 여유 기간 15점
     * + 일정 인접성 10점
     * + 생성자 참석 여부 5점
     */
    private int calculateRecommendationScore(
            PromiseTimeRecommendationResponse recommendation,
            LocalDateTime now,
            Long hostId,
            List<BusyScheduleTimeRange> busySchedules
    ) {
        int participationScore = calculateParticipationScore(recommendation);
        int timePreferenceScore = calculateTimePreferenceScore(recommendation);
        int leadTimeScore = calculateLeadTimeScore(recommendation, now);
        int adjacencyScore = calculateAdjacencyScore(recommendation, busySchedules);
        int hostAvailabilityScore = calculateHostAvailabilityScore(recommendation, hostId);

        return participationScore
                + timePreferenceScore
                + leadTimeScore
                + adjacencyScore
                + hostAvailabilityScore;
    }

    // 참여도 점수 메서드
    private int calculateParticipationScore(
            PromiseTimeRecommendationResponse recommendation
    ) {
        if (recommendation.totalMemberCount() == null
                || recommendation.totalMemberCount() == 0) {
            return 0;
        }

        return recommendation.availableMemberCount() * 50 / recommendation.totalMemberCount();
    }

    // 시간대 선호도 점수 메서드
    // 평일 18:00 ~ 21:00 → 20점
    // 주말 11:00 ~ 18:00 → 20점
    // 평일 12:00 ~ 18:00 → 10점
    // 그 외 이른 아침, 새벽, 심야 → 0점
    private int calculateTimePreferenceScore(
            PromiseTimeRecommendationResponse recommendation
    ) {
        LocalDateTime startTime = recommendation.startTime();
        LocalTime start = startTime.toLocalTime();

        boolean isWeekend =
                startTime.getDayOfWeek() == DayOfWeek.SATURDAY
                        || startTime.getDayOfWeek() == DayOfWeek.SUNDAY;

        // 주말 낮/오후 선호
        if (isWeekend) {
            if (!start.isBefore(LocalTime.of(11, 0))
                    && start.isBefore(LocalTime.of(18, 0))) {
                return 20;
            }

            // 주말이지만 너무 이르거나 늦지 않은 시간
            if (!start.isBefore(LocalTime.of(9, 0))
                    && start.isBefore(LocalTime.of(21, 0))) {
                return 10;
            }

            return 0;
        }

        // 평일 퇴근 후 선호
        if (!start.isBefore(LocalTime.of(18, 0))
                && start.isBefore(LocalTime.of(21, 0))) {
            return 20;
        }

        // 평일 낮 시간
        if (!start.isBefore(LocalTime.of(12, 0))
                && start.isBefore(LocalTime.of(18, 0))) {
            return 10;
        }

        return 0;
    }

    // 약속까지의 여유 기간 점수 메서드
    // 3일 후 ~ 10일 후 → 15점
    // 24시간 이내 ~ 2일 이내 → 5점
    // 14일 이상 이후 → 5점
    // 그 외 → 0점
    private int calculateLeadTimeScore(
            PromiseTimeRecommendationResponse recommendation,
            LocalDateTime now
    ) {
        long hoursUntilPromise =
                Duration.between(now, recommendation.startTime()).toHours();

        long daysUntilPromise = hoursUntilPromise / 24;

        if (daysUntilPromise >= 3 && daysUntilPromise <= 10) {
            return 15;
        }

        if (hoursUntilPromise >= 24 && daysUntilPromise <= 2) {
            return 5;
        }

        if (daysUntilPromise >= 11 && daysUntilPromise <= 13) {
            return 10;
        }


        if (daysUntilPromise >= 14) {
            return 5;
        }

        return 0;
    }

    // 일정 인접성 점수 메서드
    /*
        A의 최고 gap 점수 = 10점
        B의 최고 gap 점수 = 5점
        C의 최고 gap 점수 = 0점

        인접성 점수 = (10 + 5 + 0) / 3 = 5점
     */
    private int calculateAdjacencyScore(
            PromiseTimeRecommendationResponse recommendation,
            List<BusyScheduleTimeRange> busySchedules
    ) {
        Set<Long> availableMemberIds = recommendation.availableMembers().stream()
                .map(PromiseRecommendationMemberResponse::userId)
                .collect(Collectors.toSet());

        if (availableMemberIds.isEmpty()) {
            return 0;
        }

        LocalDate recommendationDate = recommendation.startTime().toLocalDate();

        LocalDateTime dayStart = recommendationDate.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        List<BusyScheduleTimeRange> expandedSchedules =
                expandBusySchedulesForDate(
                        busySchedules,
                        recommendationDate,
                        dayStart,
                        dayEnd
                );

        int totalScore = 0;

        for (Long memberId : availableMemberIds) {
            int memberBestGapScore = calculateMemberBestGapScore(
                    memberId,
                    recommendation,
                    expandedSchedules
            );

            totalScore += memberBestGapScore;
        }

        return totalScore / availableMemberIds.size();
    }

    private int calculateMemberBestGapScore(
            Long memberId,
            PromiseTimeRecommendationResponse recommendation,
            List<BusyScheduleTimeRange> expandedSchedules
    ) {
        return expandedSchedules.stream()
                .filter(schedule -> schedule.userId().equals(memberId))
                .mapToInt(schedule -> calculateGapScore(recommendation, schedule))
                .max()
                .orElse(10);
    }

    private int calculateGapScore(
            PromiseTimeRecommendationResponse recommendation,
            BusyScheduleTimeRange schedule
    ) {
        int beforeGapScore = calculateSingleGapScore(
                schedule.endTime(),
                recommendation.startTime()
        );

        int afterGapScore = calculateSingleGapScore(
                recommendation.endTime(),
                schedule.startTime()
        );

        return Math.max(beforeGapScore, afterGapScore);
    }

    private int calculateSingleGapScore(
            LocalDateTime firstEnd,
            LocalDateTime secondStart
    ) {
        if (firstEnd.isAfter(secondStart)) {
            return 0;
        }

        long gapMinutes = Duration.between(firstEnd, secondStart).toMinutes();

        // 30분 ~ 60분: 이동/준비 시간이 있어서 가장 좋음
        if (gapMinutes >= 30 && gapMinutes <= 60) {
            return 10;
        }

        // 15분 ~ 30분: 가능은 하지만 조금 빠듯함
        if (gapMinutes >= 15 && gapMinutes < 30) {
            return 7;
        }

        // 0분 ~ 15분: 너무 붙어 있어서 이동/준비 시간이 부족함
        if (gapMinutes >= 0 && gapMinutes < 15) {
            return 3;
        }

        // 60분 ~ 90분: 나쁘진 않지만 애매하게 뜸
        if (gapMinutes > 60 && gapMinutes <= 90) {
            return 5;
        }

        return 5;
    }

    // 생성자 참석 여부 점수 메서드
    private int calculateHostAvailabilityScore(
            PromiseTimeRecommendationResponse recommendation,
            Long hostId
    ) {
        boolean hostAvailable = recommendation.availableMembers().stream()
                .anyMatch(member -> member.userId().equals(hostId));

        return hostAvailable ? 5 : 0;
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


    /**
     * 약속 수정용 빈 시간 추천
     *
     * 수정용 추천 대상:
     * - 현재 약속의 생성자
     * - 현재 약속의 기존 PromiseMember들
     *
     * 주의:
     * recommendPromiseTimes()는 내부에서 로그인 유저를 자동 포함한다.
     * 따라서 여기서는 생성자를 제외한 멤버 ID만 selectedMemberIds로 넘긴다.
     */
    @Transactional(readOnly = true)
    public List<PromiseTimeRecommendationResponse> recommendPromiseUpdateTimes(
            Long userId,
            Long promiseId,
            PromiseUpdateTimeRecommendRequest request
    ) {
        // 1. 기존 약속 조회
        // findWithMembersById()는 sender, members, members.user를 함께 조회한다.
        Promise promise = promiseRepository.findWithMembersById(promiseId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_NOT_FOUND));

        // 2. 약속 생성자만 추천 기반 시간 수정을 시도할 수 있도록 검증
        validatePromiseSenderForRecommendation(promise, userId);

        // 3. 이미 확정/취소된 약속은 시간 추천 수정 대상이 아님
        validatePromisePendingForRecommendation(promise);

        // 4. 현재 약속 멤버 중 생성자를 제외한 멤버 ID 추출
        // recommendPromiseTimes()가 로그인 유저를 자동 포함하므로 생성자는 제외한다.
        List<Long> selectedMemberIds = getPromiseMemberIdsExceptSender(promise);

        // 5. 기존 빈 시간 추천 Request DTO로 변환
        PromiseTimeRecommendRequest recommendRequest = new PromiseTimeRecommendRequest(
                request.proposeStartDate(),
                request.proposeEndDate(),
                request.searchStartTime(),
                request.searchEndTime(),
                request.minDuration(),
                request.sortType(),
                selectedMemberIds
        );

        // 6. 기존 추천 알고리즘 재사용
        // roomId는 기존 약속이 속한 방 ID를 사용한다.
        return recommendPromiseTimes(
                userId,
                promise.getRoom().getId(),
                recommendRequest
        );
    }

    /**
     * 현재 약속 멤버 중 생성자를 제외한 userId 목록을 반환한다.
     *
     * 이유:
     * - recommendPromiseTimes()는 로그인 유저를 자동으로 추천 대상에 포함함
     * - 따라서 selectedMemberIds에는 생성자를 제외한 나머지 멤버만 넣어야 중복이 자연스럽게 처리됨
     */
    private List<Long> getPromiseMemberIdsExceptSender(Promise promise) {
        Long senderId = promise.getSender().getId();

        List<Long> memberIds = promise.getMembers().stream()
                .map(PromiseMember::getUser)
                .map(user -> user.getId())
                .filter(memberId -> !memberId.equals(senderId))
                .distinct()
                .toList();

        // 생성자 혼자만 있는 약속이라면 추천 기반 수정의 의미가 약하므로 예외 처리
        if (memberIds.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_MEMBER);
        }

        return memberIds;
    }

    /**
     * 약속 생성자인지 검증
     *
     * 추천 기반 수정은 약속의 시간을 바꾸는 흐름이므로
     * 약속 생성자만 가능하게 제한한다.
     */
    private void validatePromiseSenderForRecommendation(Promise promise, Long userId) {
        if (!promise.getSender().getId().equals(userId)) {
            throw new CustomException(ErrorCode.PROMISE_FORBIDDEN);
        }
    }

    /**
     * PENDING 상태의 약속인지 검증
     *
     * 이미 확정된 약속은 각 멤버의 개인 일정에 등록되었을 수 있으므로
     * 현재 구조에서는 추천 기반 시간 수정을 막는다.
     */
    private void validatePromisePendingForRecommendation(Promise promise) {
        if (promise.getStatus() != PromiseStatus.PENDING) {
            throw new CustomException(ErrorCode.PROMISE_ALREADY_CLOSED);
        }
    }
}