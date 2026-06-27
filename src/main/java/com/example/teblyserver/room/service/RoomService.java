package com.example.teblyserver.room.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.notification.domain.NotificationType;
import com.example.teblyserver.notification.service.NotificationService;
import com.example.teblyserver.room.domain.InviteStatus;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.room.domain.RoomMember;
import com.example.teblyserver.room.domain.RoomRole;
import com.example.teblyserver.room.dto.request.RoomCreateRequest;
import com.example.teblyserver.room.dto.request.RoomMemberInviteRequest;
import com.example.teblyserver.room.dto.request.RoomMemberKickRequest;
import com.example.teblyserver.room.dto.request.RoomUpdateRequest;
import com.example.teblyserver.room.dto.response.RoomDetailResponse;
import com.example.teblyserver.room.dto.response.RoomListResponse;
import com.example.teblyserver.room.dto.response.RoomMemberResponse;
import com.example.teblyserver.room.repository.RoomMemberRepository;
import com.example.teblyserver.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final NotificationService notificationService;

    /**
     * 방 생성 및 멤버 초대 로직
     * @param hostId 방을 생성하는 유저(방장)의 ID
     * @param request 방 이름, 이미지, 초대할 멤버 ID 목록
     * @return 생성된 방의 ID
     */
    @Transactional
    public Long createRoom(Long hostId, RoomCreateRequest request) {

        // 1. 방을 생성하는 방장(Host) 유저 조회
        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 2. 새로운 방(Room) 객체 생성 및 저장
        Room room = Room.create(request.name(), request.description(), request.imageUrl());
        roomRepository.save(room);

        // 3. 방장을 RoomMember로 등록 (자동으로 room.getMembers()에 추가됨)
        RoomMember.create(room, host, RoomRole.HOST, InviteStatus.ACCEPTED);

        // 4. 초대할 멤버가 있다면 RoomMember로 등록 (상태는 PENDING)
        if (request.memberIds() != null && !request.memberIds().isEmpty()) {
            // IN 쿼리로 초대할 유저들을 한 번에 조회하여 성능 최적화
            List<User> invitees = userRepository.findAllById(request.memberIds());

            for (User invitee : invitees) {
                RoomMember.create(room, invitee, RoomRole.MEMBER, InviteStatus.PENDING);
                // 초대 알림 발송
                notificationService.send(
                        invitee,
                        NotificationType.INVITATION,
                        "방 초대",
                        room.getName() + "에 초대되었어요!",
                        "/rooms/" + room.getId()
                );
            }
        }

        return room.getId();
    }

    /**
     * 참여 중이거나 초대받은 방 목록 조회
     * @param type '참여중인 방 목록','초대받은 방 목록' 중 어느 것인지 구분
     */
    public List<RoomListResponse> getRooms(Long userId, String type) {

        // 1. type 파라미터 분석 ('invited' 면 PENDING, 아니면 ('joined') ACCEPTED)
        InviteStatus targetStatus = "invited".equalsIgnoreCase(type) ? InviteStatus.PENDING : InviteStatus.ACCEPTED;

        // 2. 해당 상태의 방 목록 긁어오기
        List<Room> rooms = roomMemberRepository.findRoomsByUserIdAndInviteStatus(userId, targetStatus);

        // 3. 엔티티를 프론트엔드가 그리기 좋은 DTO로 변환
        return rooms.stream()
                .map(RoomListResponse::of)
                .toList();
    }

    /**
     * 방 상세 정보 조회 (기획 화면 상단부)
     */
    public RoomDetailResponse getRoomDetail(Long userId, Long roomId) {

        // 1. 방 정보와 멤버들을 한 번에 조회
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 권한 검증: 현재 유저가 이 방에 속해있고, 초대 상태가 'ACCEPTED' 인지 확인
        boolean isMember = room.getMembers().stream()
                .anyMatch(rm -> rm.getUser().getId().equals(userId) && rm.getInviteStatus() == InviteStatus.ACCEPTED);

        if (!isMember) {
            throw new CustomException(ErrorCode.ROOM_FORBIDDEN);
        }

        // 3. 엔티티를 화면 맞춤형 DTO로 변환하여 반환
        return RoomDetailResponse.of(room);
    }

    /**
     * 방 정보 수정
     */
    @Transactional
    public Long updateRoom(Long userId, Long roomId, RoomUpdateRequest request) {

        // 1. 수정할 방 찾기
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 권한 검증: 수정을 요청한 유저가 이 방의 '방장(HOST)'인지 확인
        boolean isHost = room.getMembers().stream()
                .anyMatch(rm -> rm.getUser().getId().equals(userId) && rm.getRole() == RoomRole.HOST);

        if (!isHost) {
            throw new CustomException(ErrorCode.ROOM_FORBIDDEN);
        }

        // 3. 엔티티 정보 업데이트
        room.updateInfo(request.name(), request.description(), request.imageUrl());

        return room.getId();
    }

    /**
     * 방 삭제 (Soft Delete)
     */
    @Transactional
    public void deleteRoom(Long userId, Long roomId) {

        // 1. 삭제할 방 찾기
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 권한 검증: 방을 삭제하려는 유저가 이 방의 '방장(HOST)'인지 확인
        boolean isHost = room.getMembers().stream()
                .anyMatch(rm -> rm.getUser().getId().equals(userId) && rm.getRole() == RoomRole.HOST);

        if (!isHost) {
            throw new CustomException(ErrorCode.ROOM_FORBIDDEN);
        }

        room.delete();
    }

    /**
     * 방 멤버 목록 조회 (ACCEPTED 멤버만)
     */
    public List<RoomMemberResponse> getMembers(Long userId, Long roomId) {

        roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        boolean isMember = roomMemberRepository.findByRoomIdAndUserIdAndIsDeletedFalse(roomId, userId)
                .stream()
                .anyMatch(rm -> rm.getInviteStatus() == InviteStatus.ACCEPTED);

        if (!isMember) {
            throw new CustomException(ErrorCode.ROOM_FORBIDDEN);
        }

        return roomMemberRepository.findByRoomIdAndInviteStatusAndIsDeletedFalse(roomId, InviteStatus.ACCEPTED)
                .stream()
                .map(RoomMemberResponse::of)
                .toList();
    }

    /**
     * 멤버 초대 (PENDING 레코드 생성)
     */
    @Transactional
    public void inviteMembers(Long userId, Long roomId, RoomMemberInviteRequest request) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        boolean isHost = roomMemberRepository.findByRoomIdAndUserIdAndIsDeletedFalse(roomId, userId)
                .stream()
                .anyMatch(rm -> rm.getRole() == RoomRole.HOST);

        if (!isHost) {
            throw new CustomException(ErrorCode.ROOM_FORBIDDEN);
        }

        List<User> invitees = userRepository.findAllById(request.userIds());

        for (User invitee : invitees) {
            List<RoomMember> existing = roomMemberRepository.findByRoomIdAndUserIdAndIsDeletedFalse(roomId, invitee.getId());

            boolean alreadyActive = existing.stream()
                    .anyMatch(rm -> rm.getInviteStatus() == InviteStatus.ACCEPTED || rm.getInviteStatus() == InviteStatus.PENDING);

            if (!alreadyActive) {
                RoomMember.create(room, invitee, RoomRole.MEMBER, InviteStatus.PENDING);
            }
        }
    }

    /**
     * 멤버 강퇴 (Soft Delete)
     */
    @Transactional
    public void kickMembers(Long userId, Long roomId, RoomMemberKickRequest request) {

        roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        boolean isHost = roomMemberRepository.findByRoomIdAndUserIdAndIsDeletedFalse(roomId, userId)
                .stream()
                .anyMatch(rm -> rm.getRole() == RoomRole.HOST);

        if (!isHost) {
            throw new CustomException(ErrorCode.ROOM_FORBIDDEN);
        }

        for (Long targetUserId : request.userIds()) {
            if (targetUserId.equals(userId)) {
                continue;
            }

            roomMemberRepository.findByRoomIdAndUserIdAndIsDeletedFalse(roomId, targetUserId)
                    .forEach(RoomMember::delete);
        }
    }

    /**
     * 방 나가기 (본인 Soft Delete)
     */
    @Transactional
    public void leaveRoom(Long userId, Long roomId) {

        roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        RoomMember member = roomMemberRepository.findByRoomIdAndUserIdAndIsDeletedFalse(roomId, userId)
                .stream()
                .filter(rm -> rm.getInviteStatus() == InviteStatus.ACCEPTED)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_FORBIDDEN));

        if (member.getRole() == RoomRole.HOST) {
            throw new CustomException(ErrorCode.HOST_CANNOT_LEAVE_ROOM);
        }

        member.delete();
    }
}
