package com.example.teblyserver.friend.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.friend.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    // 친구 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getFriends(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(friendService.getFriends(userId)));
    }

    // 초대 코드로 친구 추가
    @PostMapping("/requests/code")
    public ResponseEntity<ApiResponse<Void>> addFriendByCode(
            @AuthenticationPrincipal Long userId,
            @RequestBody java.util.Map<String, String> request) {
        friendService.addFriendByCode(userId, request.get("invite_code"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    // 링크로 친구 추가
    @PostMapping("/requests/link")
    public ResponseEntity<ApiResponse<Void>> addFriendByLink(
            @AuthenticationPrincipal Long userId,
            @RequestBody java.util.Map<String, String> request) {
        friendService.addFriendByLink(userId, request.get("invite_token"));
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
}