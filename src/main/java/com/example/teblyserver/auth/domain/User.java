package com.example.teblyserver.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String oauthId;

    private String nickname;

    private String profileImageUrl;

    private String invitationCode;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Column(nullable = false)
    private boolean isNewUser = true;

    public static User create(String email, String provider, String oauthId, String nickname, String profileImageUrl) {
        User user = new User();
        user.email = email;
        user.provider = provider;
        user.oauthId = oauthId;
        user.nickname = nickname;
        user.profileImageUrl = profileImageUrl;
        return user;
    }
}