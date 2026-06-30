package com.example.teblyserver.notification.domain;

import com.example.teblyserver.auth.domain.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 255)
    private String content;

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(length = 255)
    private String redirectPath;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Notification(User user, NotificationType type, String title, String content, String redirectPath) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.content = content;
        this.redirectPath = redirectPath;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}