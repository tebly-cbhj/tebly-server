package com.example.teblyserver.promise.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PromiseMemberStatus {
    PENDING("미응답"),    // 아직 수락/거절을 하지 않은 대기 상태
    ACCEPTED("참석"),     // 약속 초대를 수락함
    REJECTED("불참");     // 약속 초대를 거절함

    private final String description;
}
