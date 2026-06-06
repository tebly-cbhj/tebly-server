package com.example.teblyserver.auth.controller;

import com.example.teblyserver.auth.service.AuthService;
import com.example.teblyserver.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<?>> reissue(@RequestBody java.util.Map<String, String> request) {
        String refreshToken = request.get("refresh_token");
        return ResponseEntity.ok(ApiResponse.success(authService.reissue(refreshToken)));
    }

    @PostMapping("/signout")
    public ResponseEntity<ApiResponse<Void>> signout(@RequestBody java.util.Map<String, String> request) {
        String refreshToken = request.get("refresh_token");
        authService.signout(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.withdraw(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}