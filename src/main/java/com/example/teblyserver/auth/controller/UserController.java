package com.example.teblyserver.auth.controller;

import com.example.teblyserver.auth.dto.UserProfileRequest;
import com.example.teblyserver.auth.service.UserService;
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
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal Long userId,
            @RequestBody UserProfileRequest request) {
        userService.updateProfile(userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getMyProfile(userId));
    }
}