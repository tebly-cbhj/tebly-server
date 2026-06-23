package com.example.teblyserver.promise.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PromiseStatus {

    PENDING("시간 확정 대기 중"),  // 빈 시간 추천 및 투표 단계
    CONFIRMED("약속 확정됨"),     // 시간이 최종 확정된 단계
    CANCELED("약속 취소됨");      // 방장에 의해 파토난 경우

    private final String description;
}
