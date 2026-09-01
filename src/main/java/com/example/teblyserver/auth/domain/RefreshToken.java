package com.example.teblyserver.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor
public class RefreshToken {

    @Id
    private Long userId;

    private String token;

    private LocalDateTime expiresAt;

    public static RefreshToken create(Long userId, String token) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.userId = userId;
        refreshToken.token = token;
        refreshToken.expiresAt = LocalDateTime.now().plusWeeks(2);
        return refreshToken;
    }

    public void updateToken(String token) {
        this.token = token;
        this.expiresAt = LocalDateTime.now().plusWeeks(2);
    }
}