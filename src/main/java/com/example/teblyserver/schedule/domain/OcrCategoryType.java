package com.example.teblyserver.schedule.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OcrCategoryType {
    LECTURE("강의"),
    WORK("알바·근로"),
    ETC("기타");

    private final String description;
}
