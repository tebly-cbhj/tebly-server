package com.example.teblyserver.schedule.domain;

import com.example.teblyserver.auth.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // [연관관계] 일정 소유자 (1 : N)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 🔗 [연관관계] 일정 카테고리 (1 : N)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDateTime startTime; // ERD의 start_time 반영

    @Column(nullable = false)
    private LocalDateTime endTime;   // ERD의 end_time 반영

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RepeatType repeatType = RepeatType.NONE; // ERD의 기본값 'NONE' 반영

    @Column
    private Integer notificationLeadMinutes; // 5분 : 5, 10분 : 10, 1시간 : 60, 1일전 : 1440 등

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Column
    private LocalDateTime lastNotifiedAt; // 마지막으로 알림을 보낸 시각 (반복 일정 중복 알림 방지용)

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 정적 팩토리 메서드 업데이트 (OCR 로그 없이 생성할 때)
    public static Schedule create(User user, Category category, String title, LocalDateTime startTime, LocalDateTime endTime, RepeatType repeatType, Integer notificationLeadMinutes) {
        Schedule schedule = new Schedule();
        schedule.user = user;
        schedule.category = category;
        schedule.title = title;
        schedule.startTime = startTime;
        schedule.endTime = endTime;
        schedule.repeatType = repeatType;
        schedule.notificationLeadMinutes = notificationLeadMinutes;
        return schedule;
    }

    public void update(Category category, String title, LocalDateTime startTime, LocalDateTime endTime, RepeatType repeatType, Integer notificationLeadMinutes) {
        if (category != null) {
            this.category = category; // 일정 수정 시 카테고리 수정 가능
        }
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (startTime != null) {
            this.startTime = startTime;
        }
        if (endTime != null) {
            this.endTime = endTime;
        }
        if (repeatType != null) {
            this.repeatType = repeatType;
        }
        this.notificationLeadMinutes = notificationLeadMinutes;
    }

    public void delete() {
        this.isDeleted = true;
    }

    public void updateLastNotifiedAt(LocalDateTime notifiedAt) {
        this.lastNotifiedAt = notifiedAt;
    }

    // 알림이 울려야 하는 실제 시각 을 직접 계산해 주는 비즈니스 메서드를 제공
    public LocalDateTime getCalculatedNotificationTime() {
        if (this.notificationLeadMinutes == null) {
            return null;
        }
        return this.startTime.minusMinutes(this.notificationLeadMinutes);
    }
}
