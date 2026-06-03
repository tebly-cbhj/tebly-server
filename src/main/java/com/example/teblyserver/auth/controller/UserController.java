package com.example.teblyserver.auth.controller;

import com.example.teblyserver.auth.dto.UserProfileRequest;
import com.example.teblyserver.auth.dto.UserProfileResponse;
import com.example.teblyserver.auth.service.UserService;
import com.example.teblyserver.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @RequestBody UserProfileRequest request) {
        userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getMyProfile(userId)));
    }

    @GetMapping("/me/invite-code")
    public ResponseEntity<ApiResponse<?>> getInviteCode(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getInviteCode(userId)));
    }
}