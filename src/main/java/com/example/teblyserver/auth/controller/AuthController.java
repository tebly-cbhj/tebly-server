package com.example.teblyserver.auth.controller;

import com.example.teblyserver.auth.service.AuthService;
import com.example.teblyserver.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.example.teblyserver.auth.dto.TokenRefreshRequest;
import com.example.teblyserver.auth.dto.TokenRefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 새 액세스/리프레시 토큰 발급")
    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> reissue(
            @RequestBody TokenRefreshRequest request) {
        Map<String, String> tokens = authService.reissue(request.refresh_token());
        TokenRefreshResponse response = new TokenRefreshResponse(
                tokens.get("access_token"),
                tokens.get("refresh_token")
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/signout")
    public ResponseEntity<ApiResponse<Void>> signout(
            @RequestBody TokenRefreshRequest request) {
        authService.signout(request.refresh_token());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "회원 탈퇴")
    @DeleteMapping("/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long userId) {
        authService.withdraw(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}