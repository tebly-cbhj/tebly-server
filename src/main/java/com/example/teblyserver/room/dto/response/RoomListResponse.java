package com.example.teblyserver.room.dto.response;

import com.example.teblyserver.room.domain.InviteStatus;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.room.domain.RoomMember;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RoomListResponse(
        Long roomId,
        String name,
        String description,
        String imageUrl,
        int totalMemberCount,
        List<String> memberProfileImages // 동그란 프로필 사진 최대 3개
) {

    public static RoomListResponse of(Room room) {
        // 1. 방에 속한 멤버 중 '수락(ACCEPTED)' 상태인 진짜 멤버들만 추려냄
        List<RoomMember> acceptedMembers = room.getMembers().stream()
                .filter(rm -> rm.getInviteStatus() == InviteStatus.ACCEPTED)
                .toList();

        // 2. 프로필 이미지 URL만 뽑아내되, 최대 3개만 자름
        List<String> profileImages = acceptedMembers.stream()
                .map(rm -> rm.getUser().getProfileImageUrl())
                .limit(3)
                .toList();

        return new RoomListResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getImageUrl(),
                acceptedMembers.size(), // 전체 멤버 수
                profileImages
        );
    }
}
