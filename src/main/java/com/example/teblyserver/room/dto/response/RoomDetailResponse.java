package com.example.teblyserver.room.dto.response;

import com.example.teblyserver.room.domain.InviteStatus;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.room.domain.RoomMember;

import java.util.List;

public record RoomDetailResponse(
        Long roomId,
        String name,
        String description,
        String imageUrl,
        int totalMemberCount,
        List<String> memberProfileImages // 동그란 프로필 사진 렌더링용 URL 리스트 (최대 3개)
) {

    public static RoomDetailResponse of(Room room) {
        // 1. 방에 속한 멤버 중 '초대 수락(ACCEPTED)' 상태인 진짜 멤버들만 필터링
        List<RoomMember> acceptedMembers = room.getMembers().stream()
                .filter(rm -> rm.getInviteStatus() == InviteStatus.ACCEPTED)
                .toList();

        // 2. 썸네일용으로 멤버들의 프로필 이미지 URL을 추출(3개)
        List<String> profileImages = acceptedMembers.stream()
                .map(rm -> rm.getUser().getProfileImageUrl())
                .limit(3)
                .toList();

        return new RoomDetailResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getImageUrl(),
                acceptedMembers.size(), // 진짜 멤버 총인원수
                profileImages
        );
    }
}
