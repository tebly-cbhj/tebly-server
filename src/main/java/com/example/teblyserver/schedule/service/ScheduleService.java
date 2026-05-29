package com.example.teblyserver.schedule.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.schedule.domain.Category;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

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
        // 시스템 디폴트 카테고리가 '아니면서' + 카테고리 주인의 ID가 내 ID와 '다르다면' 에러 발생
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

    // 자기 자신 일정 조회
    @Transactional(readOnly = true)
    public ScheduleResponseDto getSchedules(Long userId, String view, LocalDate targetDate) {

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

        // 3. 계산된 기간으로 DB 조회
        List<Schedule> schedules = scheduleRepository.findSchedulesWithinRange(userId, startDateTime, endDateTime);

        // 4. DTO 변환 후 반환
        List<EventDto> eventDtos = schedules.stream()
                .map(schedule -> EventDto.from(schedule, userId))
                .collect(Collectors.toList());

        return ScheduleResponseDto.from(eventDtos);
    }

    /*
     * TODO: 친구 일정 조회하는 서비스 로직 구현 필요
     */


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
