package com.example.teblyserver.room.dto.response;

import com.example.teblyserver.promise.domain.Promise;
import com.example.teblyserver.promise.domain.PromiseMember;
import com.example.teblyserver.promise.domain.PromiseMemberStatus;
import com.example.teblyserver.promise.domain.PromiseStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RoomPromiseResponse(
        Long promiseId,
        String title,

        LocalDate proposeStartDate,
        LocalDate proposeEndDate,

        LocalDateTime confirmedTime,
        String location,

        PromiseStatus promiseStatus,
        PromiseMemberStatus myStatus,

        boolean isSender,

        int acceptedCount,
        int totalMemberCount
) {

    // 방 상세 화면의 약속 카드 하나를 만들기 위한 DTO 변환
    public static RoomPromiseResponse of(Promise promise, Long loginUserId) {
        List<PromiseMember> members = promise.getMembers();

        PromiseMemberStatus myStatus = members.stream()
                .filter(member -> member.getUser().getId().equals(loginUserId))
                .map(PromiseMember::getStatus)
                .findFirst()
                .orElse(null);

        int acceptedCount = (int) members.stream()
                .filter(member -> member.getStatus() == PromiseMemberStatus.ACCEPTED)
                .count();

        boolean isSender = promise.getSender().getId().equals(loginUserId);

        return new RoomPromiseResponse(
                promise.getId(),
                promise.getTitle(),

                promise.getProposeStartDate(),
                promise.getProposeEndDate(),

                promise.getConfirmedTime(),
                promise.getLocation(),

                promise.getStatus(),
                myStatus,

                isSender,

                acceptedCount,
                members.size()
        );
    }
}
