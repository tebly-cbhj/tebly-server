package com.example.teblyserver.room.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.room.dto.request.RoomCreateRequest;
import com.example.teblyserver.room.dto.request.RoomMemberInviteRequest;
import com.example.teblyserver.room.dto.request.RoomMemberKickRequest;
import com.example.teblyserver.room.dto.request.RoomUpdateRequest;
import com.example.teblyserver.room.dto.response.RoomDetailResponse;
import com.example.teblyserver.room.dto.response.RoomListResponse;
import com.example.teblyserver.room.dto.response.RoomMemberResponse;
import com.example.teblyserver.room.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /**
     * 방 생성 및 멤버 초대 API
     * URL: POST /rooms
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createRoom(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RoomCreateRequest request
    ) {

        Long roomId = roomService.createRoom(userId, request);

        // 프론트에 성공 시그널(HTTP 201 Created)과 함께 생성된 방의 ID를 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("방이 성공적으로 생성되었습니다.", roomId));
    }

    /**
     * 내가 참여 중인 방 목록 조회 API
     * URL: GET /rooms?type=joined
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomListResponse>>> getRooms(
            @AuthenticationPrincipal Long userId
    ) {
        List<RoomListResponse> response = roomService.getRooms(userId, "joined");

        return ResponseEntity.ok(ApiResponse.success("방 목록 조회 성공", response));
    }


    /**
     * 방 상세 정보 조회 API (방 정보 + 멤버 요약)
     * URL: GET /rooms/{roomId}
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomDetailResponse>> getRoomDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        RoomDetailResponse response = roomService.getRoomDetail(userId, roomId);

        // 200 OK 상태 코드와 함께 데이터 반환
        return ResponseEntity.ok(ApiResponse.success("방 상세 정보 조회 성공", response));
    }

    /**
     * 방 정보 수정 API
     * URL: PATCH /rooms/{roomId}
     */
    @PatchMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Long>> updateRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @Valid @RequestBody RoomUpdateRequest request
    ) {

        Long updatedRoomId = roomService.updateRoom(userId, roomId, request);

        return ResponseEntity.ok(ApiResponse.success("방 정보가 성공적으로 수정되었습니다.", updatedRoomId));
    }

    /**
     * 방 삭제 API (Soft Delete)
     * URL: DELETE /rooms/{roomId}
     */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {

        roomService.deleteRoom(userId, roomId);

        return ResponseEntity.ok(ApiResponse.success("방이 성공적으로 삭제되었습니다.", null));
    }

    /**
     * 방 멤버 목록 조회 API
     * URL: GET /rooms/{roomId}/members
     */
    @GetMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<List<RoomMemberResponse>>> getMembers(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        List<RoomMemberResponse> response = roomService.getMembers(userId, roomId);

        return ResponseEntity.ok(ApiResponse.success("멤버 목록 조회 성공", response));
    }

    /**
     * 멤버 초대 API (PENDING 레코드 생성)
     * URL: POST /rooms/{roomId}/members
     */
    @PostMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<Void>> inviteMembers(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @Valid @RequestBody RoomMemberInviteRequest request
    ) {
        roomService.inviteMembers(userId, roomId, request);

        return ResponseEntity.ok(ApiResponse.success("멤버 초대가 완료되었습니다.", null));
    }

    /**
     * 멤버 강퇴 API
     * URL: DELETE /rooms/{roomId}/members
     */
    @DeleteMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<Void>> kickMembers(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @Valid @RequestBody RoomMemberKickRequest request
    ) {
        roomService.kickMembers(userId, roomId, request);

        return ResponseEntity.ok(ApiResponse.success("멤버가 성공적으로 강퇴되었습니다.", null));
    }

    /**
     * 방 나가기 API
     * URL: DELETE /rooms/{roomId}/members/me
     */
    @DeleteMapping("/{roomId}/members/me")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        roomService.leaveRoom(userId, roomId);

        return ResponseEntity.ok(ApiResponse.success("방에서 나갔습니다.", null));
    }
}
