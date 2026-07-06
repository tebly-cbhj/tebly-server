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

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private String oauthId;

    private String nickname;

    private String profileImageUrl;

    private String bio;

    private String invitationCode;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Column(nullable = false)
    private boolean isNewUser = true;

    public static User create(String email, Provider provider, String oauthId, String nickname, String profileImageUrl) {
        User user = new User();
        user.email = email;
        user.provider = provider;
        user.oauthId = oauthId;
        user.nickname = nickname;
        user.profileImageUrl = profileImageUrl;
        user.invitationCode = generateNumericInvitationCode();
        return user;
    }

    private static String generateNumericInvitationCode() {
        int code = (int) (Math.random() * 1_000_000); // 0 ~ 999999
        return String.format("%06d", code); // 6자리
    }

    public void softDelete() {
        this.isDeleted = true;
    }

    public void updateProfile(String nickname, String bio, String profileImageUrl) {
        if (nickname != null) this.nickname = nickname;
        if (bio != null) this.bio = bio;
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
        this.isNewUser = false;
    }
}