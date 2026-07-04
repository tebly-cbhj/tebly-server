package com.example.teblyserver.promise.dto.response;

import com.example.teblyserver.promise.domain.PromiseMember;
import com.example.teblyserver.promise.domain.PromiseMemberStatus;

import java.time.LocalDateTime;

public record PromiseMemberResponse(
        Long promiseMemberId,
        Long userId,
        String nickname,
        String profileImageUrl,
        PromiseMemberStatus status,
        LocalDateTime lastPokedAt
) {

    public static PromiseMemberResponse from(PromiseMember member) {
        return new PromiseMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getUser().getProfileImageUrl(),
                member.getStatus(),
                member.getLastPokedAt()
        );
    }
}
