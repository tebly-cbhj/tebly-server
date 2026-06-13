package com.example.teblyserver.room.domain;

public enum InviteStatus {
    PENDING,  // 수락 대기 중
    ACCEPTED, // 수락 완료 (방에 정상 참여됨)
    REJECTED  // 거절
}
