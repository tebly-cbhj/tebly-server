package com.example.teblyserver.schedule.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RepeatType {
    NONE("반복 없음"),
    DAILY("매일 반복"),
    WEEKLY("매주 반복"),
    MONTHLY("매월 반복");

    private final String description;
}