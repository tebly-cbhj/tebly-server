package com.example.teblyserver.friend.dto;

import jakarta.validation.constraints.NotNull;

public record FriendFavoriteRequest(
        @NotNull(message = "즐겨찾기 여부는 필수입니다.")
        Boolean isFavorite
) {}
