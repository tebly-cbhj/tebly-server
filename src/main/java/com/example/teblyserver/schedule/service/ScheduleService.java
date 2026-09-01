package com.example.teblyserver.schedule.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.promise.domain.Promise;
import com.example.teblyserver.promise.domain.PromiseMemberStatus;
import com.example.teblyserver.schedule.domain.Category;
import com.example.teblyserver.schedule.domain.RepeatType;
import com.example.teblyserver.schedule.domain.Schedule;
import com.example.teblyserver.schedule.domain.ScheduleOccurrenceException;
import com.example.teblyserver.schedule.dto.request.ScheduleRequestDto;
import com.example.teblyserver.schedule.dto.request.ScheduleUpdateRequestDto;
import com.example.teblyserver.schedule.dto.response.EventDto;
import com.example.teblyserver.schedule.dto.response.ScheduleResponseDto;
import com.example.teblyserver.schedule.repository.CategoryRepository;
import com.example.teblyserver.schedule.repository.ScheduleOccurrenceExceptionRepository;
import com.example.teblyserver.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ScheduleOccurrenceExceptionRepository scheduleOccurrenceExceptionRepository;
    // private final FriendService friendService;

    // 일정 직접 추가
    @Transactional
    public Long addSchedule(Long userId, ScheduleRequestDto requestDto) {

        // 1. JWT 토큰에서 파싱되어 넘어온 userId로 DB에서 실제 User 엔티티를 찾음
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 2. DTO에서 넘어온 categoryId로 실제 Category 엔티티를 찾음
        Category category = categoryRepository.findById(requestDto.categoryId())
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        // [보안 검증] 내 일정을 만드는데 남의 커스텀 카테고리를 훔쳐 쓰지 못하도록 방어 (2차 방어)
        if (!category.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.CATEGORY_FORBIDDEN); // "해당 카테고리에 대한 권한이 없습니다."
        }

        validateScheduleTime(requestDto.startTime(),requestDto.endTime());

        // 3. DTO 데이터와 유저 엔티티를 조합해 Schedule 객체를 새로 생성
        Schedule schedule = Schedule.create(
                user,
                category,
                requestDto.title(),
                requestDto.startTime(),
                requestDto.endTime(),
                requestDto.repeatType(),
                requestDto.notificationLeadMinutes(),
                requestDto.location(),
                requestDto.memo(),
                requestDto.repeatUntil()
        );

        // 4. Repository를 통해 DB에 최종 저장
        Schedule savedSchedule = scheduleRepository.save(schedule);

        return savedSchedule.getId();
    }




    @Transactional(readOnly = true)
    public ScheduleResponseDto getSchedules(Long userId, String view, LocalDate targetDate) {

        List<EventDto> eventDtos = getExpandedEventDtos(userId, userId, view, targetDate);
        return ScheduleResponseDto.from(eventDtos);
    }


    private List<EventDto> getExpandedEventDtos(Long targetUserId, Long loginUserId, String view, LocalDate targetDate) {

        // 1. 만약 프론트에서 date를 안 보내줬다면(null), 오늘 날짜를 기본값으로 잡음
        LocalDate baseDate = (targetDate != null) ? targetDate : LocalDate.now();
        LocalDateTime startDateTime;
        LocalDateTime endDateTime;

        // 2. 주간(weekly) 또는 월간(monthly)에 따른 시작일/종료일 계산
        if ("monthly".equalsIgnoreCase(view)) {
            // 월간: 해당 월의 1일 00:00:00 ~ 마지막 날 23:59:59
            startDateTime = baseDate.withDayOfMonth(1).atStartOfDay();
            endDateTime = baseDate.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX);
        } else if ("weekly".equalsIgnoreCase(view)) { // 주간: 일요일 00:00:00 ~ 토요일 23:59:59
            LocalDate weekStart =
                    baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

            LocalDate weekEnd = weekStart.plusDays(6);

            startDateTime = weekStart.atStartOfDay();
            endDateTime = weekEnd.atTime(LocalTime.MAX);
        } else {
            // weekly도 아니고 monthly도 아니면 400 에러
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        List<Schedule> originalSchedules = scheduleRepository.findSchedulesWithinRange(targetUserId, startDateTime, endDateTime);

        Map<Long, Set<LocalDateTime>> excludedStarts =
                loadExcludedOccurrenceStarts(originalSchedules);
        List<EventDto> resultDtos = new ArrayList<>();

        // 원본 일정을 순회하며 주간/월간 뷰에 맞게 일정을 복제
        for (Schedule schedule : originalSchedules) {
            LocalDateTime currentStart = schedule.getStartTime();
            LocalDateTime currentEnd = schedule.getEndTime();

            if (schedule.getRepeatType() == RepeatType.NONE) {
                // 단건 일정
                if (!currentStart.isAfter(endDateTime) && !currentEnd.isBefore(startDateTime)) {
                    resultDtos.add(EventDto.from(schedule, loginUserId));
                }
            } else {
                // 반복 일정: 현재 계산하는 일정이 프론트가 요청한 기간을 벗어날 때까지 계속 더해가며 복제
                while (!currentStart.isAfter(endDateTime)) {
                    if (schedule.getRepeatUntil() != null && currentStart.isAfter(schedule.getRepeatUntil())) {
                        break;
                    }
                    if (!currentEnd.isBefore(startDateTime)
                            && !isExcluded(
                                excludedStarts,
                                schedule.getId(),
                                currentStart
                    )) {
                        resultDtos.add(EventDto.fromExpanded(schedule, loginUserId, currentStart, currentEnd));
                    }

                    // 다음 일정 시간으로 점프 (WEEKLY면 1주일 뒤로 이동)
                    currentStart = nextOccurrence(
                            currentStart,
                            schedule.getRepeatType()
                    );

                    currentEnd = nextOccurrence(
                            currentEnd,
                            schedule.getRepeatType()
                    );
                }
            }
        }
        return resultDtos;
    }

    private Map<Long, Set<LocalDateTime>> loadExcludedOccurrenceStarts(List<Schedule> schedules) {

        if (schedules.isEmpty()) {
            return Map.of();
        }

        List<Long> scheduleIds = schedules.stream()
                .map(Schedule::getId)
                .toList();

        List<ScheduleOccurrenceException> exceptions =
                scheduleOccurrenceExceptionRepository
                        .findAllBySchedule_IdIn(scheduleIds);

        Map<Long, Set<LocalDateTime>> result = new HashMap<>();

        for (ScheduleOccurrenceException exception : exceptions) {
            Long scheduleId = exception.getSchedule().getId();

            result.computeIfAbsent(
                    scheduleId,
                    ignored -> new HashSet<>()
            ).add(exception.getOccurrenceStartTime());
        }

        return result;
    }

    private boolean isExcluded(
            Map<Long, Set<LocalDateTime>> excludedStarts,
            Long scheduleId,
            LocalDateTime occurrenceStart
    ) {
        return excludedStarts
                .getOrDefault(scheduleId, Set.of())
                .contains(occurrenceStart);
    }

    /**
     * 친구 일정 조회 (Strategy B 적용)
     */
    @Transactional(readOnly = true)
    public ScheduleResponseDto getFriendSchedules(Long myUserId, Long friendId, String view, LocalDate targetDate) {

        List<EventDto> eventDtos = getExpandedEventDtos(friendId, myUserId, view, targetDate);
        return ScheduleResponseDto.from(eventDtos);
    }


    // 일정 수정
    @Transactional
    public Long updateSchedule(Long userId, Long scheduleId, ScheduleUpdateRequestDto dto) {
        // 1. 수정할 일정을 조회
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));

        // 2. 권한 검증: 일정을 생성한 유저와 수정을 요청한 로그인 유저가 일치하는지 확인
        if (!schedule.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.SCHEDULE_FORBIDDEN);
        }

        /*
         * 3. 수정하기 전 반복 규칙을 보관한다.
         *
         * LocalDateTime과 RepeatType은 변경 불가능 객체/Enum이므로
         * 현재 값을 그대로 변수에 보관해도 된다.
         */
        LocalDateTime oldStartTime = schedule.getStartTime();
        RepeatType oldRepeatType = schedule.getRepeatType();

        // 4. [카테고리 변경 처리 및 권한 검증]
        // DTO에 categoryId가 넘어왔다면, 해당 카테고리를 조회하고 내 것인지 검증
        Category category = null;
        if (dto.categoryId() != null) {
            category = categoryRepository.findById(dto.categoryId())
                    .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

            // 새로 바꾸려는 카테고리의 주인이 내가 맞는지 확인
            if (!category.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.CATEGORY_FORBIDDEN);
            }
        }

        // 5. 수정 요청에 값이 없으면 기존 시작/종료 시각 사용
        LocalDateTime newStartTime = dto.startTime() != null ? dto.startTime() : schedule.getStartTime();
        LocalDateTime newEndTime = dto.endTime() != null ? dto.endTime() : schedule.getEndTime();
        validateScheduleTime(newStartTime, newEndTime);


        // 6. 실제 Schedule Entity 수정
        schedule.update(
                category,
                dto.title(),
                dto.startTime(),
                dto.endTime(),
                dto.repeatType(),
                dto.notificationLeadMinutes(),
                dto.location(),
                dto.memo(),
                dto.repeatUntil()
        );


        /*
         * 7. 수정 전 값과 수정 후 값을 비교한다.
         *
         * Objects.equals를 사용하면 혹시 값이 null이어도
         * NullPointerException 없이 비교할 수 있다.
         */
        boolean startTimeChanged =
                !Objects.equals(
                        oldStartTime,
                        schedule.getStartTime()
                );

        boolean repeatTypeChanged =
                oldRepeatType != schedule.getRepeatType();

        boolean recurrenceRuleChanged =
                startTimeChanged || repeatTypeChanged;

        /*
         * 8. 시작 시각 또는 반복 유형이 변경되었다면
         * 기존 특정 회차 삭제 예외를 모두 제거한다.
         */
        if (recurrenceRuleChanged) {
            scheduleOccurrenceExceptionRepository
                    .deleteAllBySchedule_Id(scheduleId);
        }

        // Dirty checking으로 Schedule 변경 내용 반영
        return schedule.getId();
    }

    // 일정 삭제
    @Transactional
    public void deleteSchedule(Long userId, Long scheduleId) {
        // 1. 삭제할 일정을 조회
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));

        // 2. 권한 검증: 일정을 생성한 유저와 삭제를 요청한 유저가 일치하는지 확인
        if (!schedule.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.SCHEDULE_FORBIDDEN);
        }

        // 3. soft-delete 수행 (상태값만 true로 변경)
        schedule.delete();
    }

    // 반복 일정 중 단일 회차 삭제
    @Transactional
    public void deleteScheduleOccurrence(
            Long userId,
            Long scheduleId,
            LocalDateTime occurrenceStart
    ) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.SCHEDULE_NOT_FOUND)
                );

        if (!schedule.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.SCHEDULE_FORBIDDEN);
        }

        if (schedule.getRepeatType() == RepeatType.NONE) {
            throw new CustomException(
                    ErrorCode.INVALID_SCHEDULE_OCCURRENCE
            );
        }

        // 실제 반복 일정인지 확인 -> 아니라면 예외
        // 이 검증이 없으면 매주 화요일 일정에 대해 실수로 수요일 날짜를 보내도 예외 데이터가 저장됨
        if (!isValidOccurrenceStart(schedule, occurrenceStart)) {
            throw new CustomException(ErrorCode.INVALID_SCHEDULE_OCCURRENCE);
        }

        // 동일 요청을 여러 번 보내더라도 성공으로 처리
        boolean alreadyDeleted = scheduleOccurrenceExceptionRepository
                        .existsBySchedule_IdAndOccurrenceStartTime(scheduleId, occurrenceStart);

        if (alreadyDeleted) {
            return;
        }

        ScheduleOccurrenceException exception =
                ScheduleOccurrenceException.create(
                        schedule,
                        occurrenceStart
                );

        scheduleOccurrenceExceptionRepository.save(exception);
    }

    // 실제 반복일정인지 확인하는 로직
    private boolean isValidOccurrenceStart(
            Schedule schedule,
            LocalDateTime requestedStart
    ) {
        LocalDateTime current = schedule.getStartTime();

        if (requestedStart.isBefore(current)) {
            return false;
        }

        if (schedule.getRepeatUntil() != null
                && requestedStart.isAfter(schedule.getRepeatUntil())) {
            return false;
        }

        while (current.isBefore(requestedStart)) {
            current = nextOccurrence(
                    current,
                    schedule.getRepeatType()
            );
        }

        return current.equals(requestedStart);
    }

    private LocalDateTime nextOccurrence(
            LocalDateTime current,
            RepeatType repeatType
    ) {
        return switch (repeatType) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case MONTHLY -> current.plusMonths(1);
            case YEARLY -> current.plusYears(1);
            case NONE -> throw new CustomException(
                    ErrorCode.INVALID_SCHEDULE_OCCURRENCE
            );
        };
    }


    /**
     * 약속 확정 시,
     * 약속을 수락한 멤버들의 개인 일정에 자동 등록
     */
    @Transactional
    public void addPromiseSchedules(Promise promise) {

        // 생성자가 약속 생성 시 선택한 카테고리 이름
        String promiseCategoryName = promise.getCategory().getName();

        List<Schedule> schedules = promise.getMembers().stream()
                // 참석으로 응답한 멤버만 일정 등록
                .filter(member -> member.getStatus() == PromiseMemberStatus.ACCEPTED)
                .map(member -> {
                    User user = member.getUser();

                    // 각 유저의 기본 카테고리 중, 생성자가 선택한 카테고리와 이름이 같은 카테고리를 찾음
                    Category memberCategory = categoryRepository
                            .findByUserIdAndNameAndIsDefaultTrue(user.getId(), promiseCategoryName)
                            .orElseThrow(() -> new CustomException(ErrorCode.DEFAULT_CATEGORY_MISSING));

                    return Schedule.create(
                            user,
                            memberCategory,
                            promise.getTitle(),
                            promise.getStartTime(),
                            promise.getEndTime(),
                            RepeatType.NONE,
                            promise.getNotificationLeadMinutes()
                    );
                })
                .toList();

        scheduleRepository.saveAll(schedules);
    }

    private void validateScheduleTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        if (!startTime.isBefore(endTime)) {
            throw new CustomException(ErrorCode.INVALID_SCHEDULE_TIME);
        }
    }
}
