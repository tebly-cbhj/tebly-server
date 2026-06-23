package com.example.teblyserver.promise.dto.response;

import com.example.teblyserver.promise.domain.Promise;
import com.example.teblyserver.promise.domain.PromiseMember;
import com.example.teblyserver.promise.domain.PromiseMemberStatus;
import com.example.teblyserver.promise.domain.PromiseStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PromiseDetailResponse(
        Long promiseId,

        Long roomId,
        String roomName,

        Long categoryId,
        String categoryName,

        String title,
        String comment,

        LocalDate proposeStartDate,
        LocalDate proposeEndDate,

        LocalDateTime confirmedTime,
        String location,

        PromiseStatus status,
        Integer minDuration,

        PromiseMemberStatus myStatus,
        boolean isSender,

        int totalMemberCount,
        int acceptedCount,
        int rejectedCount,
        int pendingCount,

        List<PromiseMemberResponse> members
) {

    /**
     * Promise 엔티티를 PromiseDetailResponse DTO로 변환하는 정적 메서드
     *
     * @param promise 조회한 약속 엔티티
     * @param loginUserId 현재 로그인한 사용자 ID
     * @return 약속 상세 조회 응답 DTO
     */
    public static PromiseDetailResponse from(Promise promise, Long loginUserId) {

        // 약속에 참여하는 멤버 목록
        List<PromiseMember> promiseMembers = promise.getMembers();

        // PromiseMember 엔티티 목록을 PromiseMemberResponse DTO 목록으로 변환
        List<PromiseMemberResponse> memberResponses = promiseMembers.stream()
                .map(PromiseMemberResponse::from)
                .toList();

        // 참석 상태인 멤버 수 계산
        int acceptedCount = (int) promiseMembers.stream()
                .filter(member -> member.getStatus() == PromiseMemberStatus.ACCEPTED)
                .count();

        // 불참 상태인 멤버 수 계산
        int rejectedCount = (int) promiseMembers.stream()
                .filter(member -> member.getStatus() == PromiseMemberStatus.REJECTED)
                .count();

        // 미응답 상태인 멤버 수 계산
        int pendingCount = (int) promiseMembers.stream()
                .filter(member -> member.getStatus() == PromiseMemberStatus.PENDING)
                .count();

        // 현재 로그인한 사용자의 약속 응답 상태
        PromiseMemberStatus myStatus = promiseMembers.stream()
                .filter(member -> member.getUser().getId().equals(loginUserId))
                .map(PromiseMember::getStatus)
                .findFirst()
                .orElse(null);

        // 현재 로그인한 사용자가 약속 생성자인지 여부
        boolean isSender = promise.getSender().getId().equals(loginUserId);

        return new PromiseDetailResponse(
                promise.getId(),

                promise.getRoom().getId(),
                promise.getRoom().getName(),

                promise.getCategory() == null ? null : promise.getCategory().getId(),
                promise.getCategory() == null ? null : promise.getCategory().getName(),

                promise.getTitle(),
                promise.getComment(),

                promise.getProposeStartDate(),
                promise.getProposeEndDate(),

                promise.getConfirmedTime(),
                promise.getLocation(),

                promise.getStatus(),
                promise.getMinDuration(),

                myStatus,

                isSender,

                promiseMembers.size(),
                acceptedCount,
                rejectedCount,
                pendingCount,

                memberResponses
        );
    }
}
