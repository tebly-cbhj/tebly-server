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
import java.util.ArrayList;
import java.util.List;

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

    // [연관관계] 일정 카테고리 (1 : N)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column
    private String location;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column
    private LocalDateTime repeatUntil; // 반복 종료일

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RepeatType repeatType = RepeatType.NONE;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleReminder> reminders = new ArrayList<>();

    @Column(nullable = false)
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 정적 팩토리 메서드 업데이트 (전체 필드 포함)
    public static Schedule create(
            User user,
            Category category,
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime,
            RepeatType repeatType,
            List<Integer> notificationLeadMinutesList,
            String location,
            String memo,
            LocalDateTime repeatUntil
    ) {
        Schedule schedule = new Schedule();
        schedule.user = user;
        schedule.category = category;
        schedule.title = title;
        schedule.startTime = startTime;
        schedule.endTime = endTime;
        schedule.repeatType = repeatType;
        schedule.location = location;
        schedule.memo = memo;
        schedule.repeatUntil = repeatUntil;
        schedule.applyReminders(notificationLeadMinutesList);
        return schedule;
    }


    // 기존 create 메서드 오버로딩 (OCR 및 약속 확정 일정 생성 시 하위 호환성 유지)
    public static Schedule create(User user,
                                  Category category,
                                  String title,
                                  LocalDateTime startTime,
                                  LocalDateTime endTime,
                                  RepeatType repeatType,
                                  List<Integer> notificationLeadMinutesList) {
        return create(user, category, title, startTime, endTime, repeatType,
                notificationLeadMinutesList, null, null, null);
    }


    public void update(Category category, String title, LocalDateTime startTime,
                       LocalDateTime endTime, RepeatType repeatType, List<Integer> notificationLeadMinutesList,
                       String location, String memo, LocalDateTime repeatUntil) {
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
        if (notificationLeadMinutesList != null) {
            applyReminders(notificationLeadMinutesList);
        }
        this.location = location;
        this.memo = memo;
        this.repeatUntil = repeatUntil;
    }

    private void applyReminders(List<Integer> leadMinutesList) {
        this.reminders.clear();
        if (leadMinutesList == null) {
            return;
        }
        leadMinutesList.stream()
                .distinct()
                .forEach(leadMinutes -> this.reminders.add(ScheduleReminder.create(this, leadMinutes)));
    }


    public void delete() {
        this.isDeleted = true;
    }

}
