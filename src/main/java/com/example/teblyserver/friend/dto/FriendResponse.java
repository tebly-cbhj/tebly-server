package com.example.teblyserver.friend.dto;

import com.example.teblyserver.auth.domain.User;

public record FriendResponse(
        Long id,
        String nickname,
        String bio,
        String profileImageUrl,
        boolean isFavorite
) {

    public static FriendResponse of(User user, boolean isFavorite) {
        return new FriendResponse(
                user.getId(),
                user.getNickname(),
                user.getBio(),
                user.getProfileImageUrl(),
                isFavorite
        );
    }

    // 미리보기 등 아직 친구 관계가 아닌 상대를 반환할 때 (즐겨찾기 여부 없음 → false)
    public static FriendResponse of(User user) {
        return of(user, false);
    }
}
