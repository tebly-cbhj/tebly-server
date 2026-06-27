package com.example.teblyserver.promise.dto.response;

import java.time.LocalDateTime;

public record PromisePokeResponse(
        Long targetUserId,
        LocalDateTime pokedAt
) {
}
