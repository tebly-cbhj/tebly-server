package com.example.teblyserver.schedule.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.schedule.domain.Category;
import com.example.teblyserver.schedule.domain.RepeatType;
import com.example.teblyserver.schedule.domain.Schedule;
import com.example.teblyserver.schedule.dto.request.ScheduleUpdateRequestDto;
import com.example.teblyserver.schedule.dto.response.ScheduleResponseDto;
import com.example.teblyserver.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock // 가짜(Mock) 객체 생성 (실제 DB 연결 X)
    private ScheduleRepository scheduleRepository;

    @InjectMocks // 위에 만든 가짜 객체들을 주입받는 테스트 대상
    private ScheduleService scheduleService;

    @Test
    @DisplayName("주간(weekly) 일정 조회 시 날짜 계산과 변환이 성공해야 한다.")
    void getSchedules_weekly_success() {
        // Given (준비)
        Long userId = 1L;
        LocalDate targetDate = LocalDate.of(2026, 5, 27); // 기준일 세팅

        // 가짜 일정 객체 생성
        User user = new User();
        ReflectionTestUtils.setField(user, "id", userId);

        Category category = Category.createCustom(user, "전공수업", "book_icon_url", false);
        ReflectionTestUtils.setField(category, "id", 100L);

        Schedule mockSchedule = Schedule.create(
                user,
                category,
                "테스트 일정",
                LocalDateTime.of(2026, 5, 27, 10, 0),
                LocalDateTime.of(2026, 5, 27, 11, 0),
                RepeatType.NONE,
                List.of(10)
        );

        given(scheduleRepository.findSchedulesWithinRange(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of(mockSchedule));

        // When (실행)
        ScheduleResponseDto response = scheduleService.getSchedules(userId, "weekly", targetDate);

        // Then (검증)
        assertThat(response).isNotNull();

        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).title()).isEqualTo("테스트 일정");

        assertThat(response.events().get(0).category().categoryId()).isEqualTo(100L);
        assertThat(response.events().get(0).category().categoryName()).isEqualTo("전공수업");
        assertThat(response.events().get(0).category().isPrivate()).isFalse();
    }


    @Test
    @DisplayName("다른 유저의 일정을 수정하려고 시도하면 SCHEDULE_FORBIDDEN 예외가 터져야 한다.")
    void updateSchedule_notOwner_throwsException() {
        // Given (준비)
        Long loginUserId = 1L; // 지금 수정을 시도하는 다른 유저
        Long scheduleOwnerId = 2L; // 원래 일정 주인
        Long scheduleId = 100L;

        // 일정 주인(2번 유저) 객체 생성 및 가짜 일정 생성
        User owner = new User();
        ReflectionTestUtils.setField(owner, "id", scheduleOwnerId);

        Category category = Category.createCustom(owner, "기존 카테고리", "default_icon", false);

        // DB가 생성해주는 PK(id) 값만 리플렉션으로 주입
        ReflectionTestUtils.setField(category, "id", 10L);

        Schedule mockSchedule = Schedule.create(
                owner,
                category,
                "진짜 주인의 일정",
                LocalDateTime.now(),
                LocalDateTime.now(),
                RepeatType.NONE,
                List.of(10)
        );

        given(scheduleRepository.findById(scheduleId)).willReturn(Optional.of(mockSchedule));

        ScheduleUpdateRequestDto requestDto = new ScheduleUpdateRequestDto(
                null, // 카테고리는 그대로 둘게~ 하고 요청하는 상황 가정
                "해킹 시도",
                LocalDateTime.now(),
                LocalDateTime.now(),
                RepeatType.NONE,
                List.of(10)
        );

        // When & Then (실행 및 검증)
        // 1번 유저(loginUserId)가 100번 일정을 수정하려고 하면 403 에러가 터져야 성공
        CustomException exception = assertThrows(CustomException.class, () -> {
            scheduleService.updateSchedule(loginUserId, scheduleId, requestDto);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SCHEDULE_FORBIDDEN);
    }
}