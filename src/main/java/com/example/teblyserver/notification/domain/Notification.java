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

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "schedule_name", length = 100)
    private String scheduleName;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "room_name", length = 100)
    private String roomName;

    @Column(name = "target_time")
    private LocalDateTime targetTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Notification(User user, NotificationType type, String title, String content, String redirectPath,
                        Long categoryId, Long scheduleId, String scheduleName,
                        Long roomId, String roomName, LocalDateTime targetTime) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.content = content;
        this.redirectPath = redirectPath;
        this.categoryId = categoryId;
        this.scheduleId = scheduleId;
        this.scheduleName = scheduleName;
        this.roomId = roomId;
        this.roomName = roomName;
        this.targetTime = targetTime;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}