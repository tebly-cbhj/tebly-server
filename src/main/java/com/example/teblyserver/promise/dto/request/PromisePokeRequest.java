package com.example.teblyserver.promise.dto.request;

import jakarta.validation.constraints.NotNull;

public record PromisePokeRequest(
        // 콕찌르기를 보낼 대상 유저 ID
        @NotNull(message = "콕찌르기 대상 유저 ID는 필수입니다.")
        Long targetUserId
) {
}
