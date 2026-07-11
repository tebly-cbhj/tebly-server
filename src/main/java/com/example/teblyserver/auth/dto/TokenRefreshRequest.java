package com.example.teblyserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenRefreshRequest(
        @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
        String refresh_token
) {}