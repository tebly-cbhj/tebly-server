package com.example.teblyserver.schedule.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_reminders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    // 몇 분 전에 알림을 보낼지 (5, 10, 60, 1440 등)
    @Column(name = "lead_minutes", nullable = false)
    private Integer leadMinutes;

    // 이 알림 시간에 대해 마지막으로 알림을 보낸 시각 (반복 일정 중복 발송 방지용)
    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    public static ScheduleReminder create(Schedule schedule, Integer leadMinutes) {
        ScheduleReminder reminder = new ScheduleReminder();
        reminder.schedule = schedule;
        reminder.leadMinutes = leadMinutes;
        return reminder;
    }

    public void updateLastNotifiedAt(LocalDateTime notifiedAt) {
        this.lastNotifiedAt = notifiedAt;
    }
}