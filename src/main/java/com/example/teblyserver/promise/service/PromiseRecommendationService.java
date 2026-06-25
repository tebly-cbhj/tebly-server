package com.example.teblyserver.promise.service;

import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.promise.dto.internal.BusyScheduleTimeRange;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendRequest;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendationSortType;
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

        List<PromiseTimeRecommendationResponse> candidates = new ArrayList<>(); // 추천 빈 시간을 담을 리스트

        LocalDate currentDate = request.proposeStartDate();


        while (!currentDate.isAfter(request.proposeEndDate())) {
            List<PromiseTimeRecommendationResponse> dailyRecommendations =
                    findDailyRecommendations(
                            currentDate,
                            request,
                            memberIds,
                            busySchedules,
                            totalMemberCount,
                            now
                    );

            candidates.addAll(dailyRecommendations);

            if (candidates.size() >= MAX_CANDIDATE_COUNT) {
                break;
            }

            currentDate = currentDate.plusDays(1);
        }

        return candidates.stream()
                .sorted(getRecommendationComparator(request.sortType()))
                .limit(RESPONSE_RECOMMENDATION_COUNT)
                .toList();
    }


    private List<PromiseTimeRecommendationResponse> findDailyRecommendations(
            LocalDate date,
            PromiseTimeRecommendRequest request,
            List<Long> memberIds,
            List<BusyScheduleTimeRange> busySchedules,
            int totalMemberCount,
            LocalDateTime now
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
                totalMemberCount
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
            int totalMemberCount
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
                                    "멤버 전원 가능한 시간"
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

    private Comparator<PromiseTimeRecommendationResponse> getRecommendationComparator(
            PromiseTimeRecommendationSortType sortType
    ) {
        // 변경: sortType이 null이면 기본값은 빠른 시간순
        PromiseTimeRecommendationSortType effectiveSortType =
                sortType == null ? PromiseTimeRecommendationSortType.EARLIEST : sortType;

        // 변경: 프론트가 보낸 정렬 조건에 따라 다른 정렬 기준 적용
        return switch (effectiveSortType) {
            // 빠른 시간순
            case EARLIEST -> Comparator.comparing(PromiseTimeRecommendationResponse::startTime);

            // 늦은 시간순
            case LATEST -> Comparator.comparing(PromiseTimeRecommendationResponse::startTime)
                    .reversed();

            // 긴 시간순
            // 시간이 긴 후보를 먼저 보여주고,
            // 길이가 같으면 시작 시간이 빠른 순으로 정렬
            case LONGEST -> Comparator.comparing(PromiseTimeRecommendationResponse::durationMinutes)
                    .reversed()
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
                || request.searchEndTime().getMinute() % SLOT_MINUTES != 0) {
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
}
