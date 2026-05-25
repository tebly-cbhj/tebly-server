package com.example.teblyserver.schedule.controller;

import com.example.teblyserver.schedule.dto.request.ScheduleRequestDto;
import com.example.teblyserver.schedule.dto.request.ScheduleUpdateRequestDto;
import com.example.teblyserver.schedule.dto.response.ScheduleResponseDto;
import com.example.teblyserver.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     *  유저 일정 직접 추가 API
     * URL: POST /schedules/events
     */
    @PostMapping("/events")
    public ResponseEntity<Long> addSchedule(
            @AuthenticationPrincipal Long userId,
            @RequestBody ScheduleRequestDto requestDto
            ) {

        Long scheduleId = scheduleService.addSchedule(userId, requestDto);

        // 프로트에 성공 시그널(HTTP 201 Created)과 함께 일정 ID를 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleId);
    }

    /**
     *  유저 일정 전체 조회 API
     * URL: GET /schedules?view=weekly&date=2026-05-25
     */
    @GetMapping
    public ResponseEntity<ScheduleResponseDto> getSchedules(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "weekly") String view,
            // YYYY-MM-DD 포맷의 문자열을 자바의 LocalDate 객체로 자동 파싱합니다.
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date
    ) {

        ScheduleResponseDto response = scheduleService.getSchedules(userId, view, date);
        return ResponseEntity.ok(response); // 200 OK 상태 코드와 함께 데이터를 반환합니다.
    }

    /**
     *  일정 정보 수정 API
     * URL: PATCH /schedules/events/{scheduleId}
     */
    @PatchMapping("/events/{scheduleId}")
    public ResponseEntity<Long> updateSchedule(
            @AuthenticationPrincipal Long userId, // 로그인한 유저 ID
            @PathVariable Long scheduleId,        // URL 경로에서 가져온 일정 PK
            @RequestBody ScheduleUpdateRequestDto requestDto // 수정할 내용들
    ) {

        Long updatedId = scheduleService.updateSchedule(userId, scheduleId, requestDto);
        return ResponseEntity.ok(updatedId); // 200 OK 상태 코드와 함께 수정된 일정 ID 반환
    }

    /**
     *  일정 삭제 API (Soft Delete)
     * URL: DELETE /schedules/events/{scheduleId}
     */
    @DeleteMapping("/events/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            @AuthenticationPrincipal Long userId, // 로그인한 유저 ID
            @PathVariable Long scheduleId         // URL 경로에서 가져온 일정 PK
    ) {

        scheduleService.deleteSchedule(userId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
