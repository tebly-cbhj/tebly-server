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
        List<String> memberProfileImages, // 동그란 프로필 사진 최대 3개
        long unreadCount   // 안읽은 채팅 메시지 개수
) {

    public static RoomListResponse of(Room room, long unreadCount) {
        List<RoomMember> acceptedMembers = room.getMembers().stream()
                .filter(rm -> rm.getInviteStatus() == InviteStatus.ACCEPTED)
                .toList();

        List<String> profileImages = acceptedMembers.stream()
                .map(rm -> rm.getUser().getProfileImageUrl())
                .limit(3)
                .toList();

        return new RoomListResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getImageUrl(),
                acceptedMembers.size(),
                profileImages,
                unreadCount
        );
    }
}
