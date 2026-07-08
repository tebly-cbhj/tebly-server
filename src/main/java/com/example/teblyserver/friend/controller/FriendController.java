package com.example.teblyserver.friend.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.friend.dto.FriendCodeRequest;
import com.example.teblyserver.friend.dto.FriendLinkRequest;
import com.example.teblyserver.friend.dto.FriendResponse;
import com.example.teblyserver.friend.service.FriendService;
import com.example.teblyserver.schedule.dto.response.ScheduleResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    // 친구 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getFriends(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(friendService.getFriends(userId)));
    }

    // 초대 코드로 친구 추가
    @PostMapping("/requests/code")
    public ResponseEntity<ApiResponse<Void>> addFriendByCode(
            @AuthenticationPrincipal Long userId,
            @RequestBody FriendCodeRequest request) {
        friendService.addFriendByCode(userId, request.inviteCode());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 링크로 친구 추가
    @PostMapping("/requests/link")
    public ResponseEntity<ApiResponse<Void>> addFriendByLink(
            @AuthenticationPrincipal Long userId,
            @RequestBody FriendLinkRequest request) {
        friendService.addFriendByLink(userId, request.inviteToken());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 친구 삭제
    @DeleteMapping("/{friendId}")
    public ResponseEntity<ApiResponse<Void>> deleteFriend(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long friendId) {
        friendService.deleteFriend(userId, friendId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 친구 일정 조회
    @GetMapping("/{friendId}/schedules")
    public ResponseEntity<ApiResponse<ScheduleResponseDto>> getFriendSchedule(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long friendId,
            @RequestParam String view,
            @RequestParam(required = false) LocalDate targetDate) {
        return ResponseEntity.ok(ApiResponse.success(
                friendService.getFriendSchedule(userId, friendId, view, targetDate)));
    }
}