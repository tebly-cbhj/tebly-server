package com.example.teblyserver.schedule.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.schedule.domain.Category;
import com.example.teblyserver.schedule.domain.RepeatType;
import com.example.teblyserver.schedule.domain.Schedule;
import com.example.teblyserver.schedule.dto.request.ScheduleRequestDto;
import com.example.teblyserver.schedule.dto.request.ScheduleUpdateRequestDto;
import com.example.teblyserver.schedule.dto.response.EventDto;
import com.example.teblyserver.schedule.dto.response.ScheduleResponseDto;
import com.example.teblyserver.schedule.repository.CategoryRepository;
import com.example.teblyserver.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
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

        // 3. DTO 데이터와 유저 엔티티를 조합해 Schedule 객체를 새로 생성
        Schedule schedule = Schedule.create(
                user,
                category,
                requestDto.title(),
                requestDto.startTime(),
                requestDto.endTime(),
                requestDto.repeatType()
        );

        // 4. Repository를 통해 DB에 최종 저장
        Schedule savedSchedule = scheduleRepository.save(schedule);

        return savedSchedule.getId();
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
        } else if ("weekly".equalsIgnoreCase(view)) {
            // 주간 (기본값): 월요일 00:00:00 ~ 일요일 23:59:59 (ISO 표준 기준)
            startDateTime = baseDate.with(DayOfWeek.MONDAY).atStartOfDay();
            endDateTime = baseDate.with(DayOfWeek.SUNDAY).atTime(LocalTime.MAX);
        } else {
            // weekly도 아니고 monthly도 아니면 400 에러
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        List<Schedule> originalSchedules = scheduleRepository.findSchedulesWithinRange(targetUserId, startDateTime, endDateTime);
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

                    if (!currentEnd.isBefore(startDateTime)) {
                        resultDtos.add(EventDto.fromExpanded(schedule, loginUserId, currentStart, currentEnd));
                    }

                    // 다음 일정 시간으로 점프 (WEEKLY면 1주일 뒤로 이동)
                    switch (schedule.getRepeatType()) {
                        case DAILY -> {
                            currentStart = currentStart.plusDays(1);
                            currentEnd = currentEnd.plusDays(1);
                        }
                        case WEEKLY -> {
                            currentStart = currentStart.plusWeeks(1);
                            currentEnd = currentEnd.plusWeeks(1);
                        }
                        case MONTHLY -> {
                            currentStart = currentStart.plusMonths(1);
                            currentEnd = currentEnd.plusMonths(1);
                        }
                        default -> {
                            throw new CustomException(ErrorCode.INVALID_INPUT);
                        }
                    }
                }
            }
        }
        return resultDtos;
    }



    // 자기 자신 일정 조회
    @Transactional(readOnly = true)
    public ScheduleResponseDto getSchedules(Long userId, String view, LocalDate targetDate) {

        List<EventDto> eventDtos = getExpandedEventDtos(userId, userId, view, targetDate);
        return ScheduleResponseDto.from(eventDtos);
    }

    /**
     * 친구 일정 조회 (Strategy B 적용)
     */
    @Transactional(readOnly = true)
    public ScheduleResponseDto getFriendSchedules(Long myUserId, Long friendId, String view, LocalDate targetDate) {

        // =====================================================================
        // TODO: 이 부분에 Friend 도메인의 친구 확인 로직을 연결해 주세요!
        // boolean isFriend = friendService.isFriend(myUserId, friendId);
        // =====================================================================

        // (임시) 테스트위해 무조건 친구라고 가정하고 통과
        boolean isFriend = true;

        if (!isFriend) {
            // TODO: (에러 코드도 추가해주세요)
            //throw new CustomException(ErrorCode.FRIEND_FORBIDDEN);
        }

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

        // 3. [카테고리 변경 처리 및 권한 검증]
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


        // 4. 엔티티의 값을 변경
        schedule.update(category, dto.title(), dto.startTime(), dto.endTime(), dto.repeatType());

        // 별도로 repository.save()를 하지 않아도 됨, @Transactional 덕분에 Dirty-checking
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
}
