package com.example.teblyserver.decision.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDateTime;

/**
 * 후보 슬롯 바로 앞 또는 뒤에 인접한 멤버의 개인 일정.
 * gapMinutes: 슬롯과의 간격(분).
 * - 직전 일정이면 "일정 종료 ~ 슬롯 시작" 사이 간격
 * - 직후 일정이면 "슬롯 종료 ~ 일정 시작" 사이 간격
 * 간격이 짧을수록(예: 30분 이하) 그 멤버가 슬롯에 촉박하게 참여/이탈해야 함을 의미한다.
 */
public record AdjacentScheduleDto(
        String title,

        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        LocalDateTime startTime,

        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        LocalDateTime endTime,

        long gapMinutes
) {
}
