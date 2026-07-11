package com.example.teblyserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenRefreshResponse(
        @Schema(description = "새 액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
        String access_token,

        @Schema(description = "새 리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
        String refresh_token
) {}