package com.example.teblyserver.auth.controller;

import com.example.teblyserver.auth.dto.ProfileImageUploadResponse;
import com.example.teblyserver.auth.dto.UserProfileRequest;
import com.example.teblyserver.auth.dto.UserProfileResponse;
import com.example.teblyserver.auth.service.UserService;
import com.example.teblyserver.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserProfileRequest request) {
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

    @PostMapping(value = "/me/profile-image", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProfileImageUploadResponse>> uploadProfileImage(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {
        String imageUrl = userService.updateProfileImage(userId, file);
        return ResponseEntity.ok(ApiResponse.success(new ProfileImageUploadResponse(imageUrl)));
    }
}