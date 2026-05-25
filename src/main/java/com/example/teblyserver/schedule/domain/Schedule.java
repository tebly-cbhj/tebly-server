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
    // TODO: Category 엔티티 구현 후 주석 해제 예정
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "category_id", nullable = false)
    // private Category category;

    // 🔗 [이번에 추가된 꿀필드!] OCR 인식 기록 연관관계 (1 : N)
    // 이미지로 생성된 일정이 아닐 수도 있으므로 nullable = true(기본값)로 둡니다.
    // TODO: OcrLog 엔티티 구현 후 주석 해제 예정
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "ocr_log_id", nullable = true)
    // private OcrLog ocrLog;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDateTime startTime; // ERD의 start_time 반영

    @Column(nullable = false)
    private LocalDateTime endTime;   // ERD의 end_time 반영

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RepeatType repeatType = RepeatType.NONE; // ERD의 기본값 'NONE' 반영

    @Column(nullable = false)
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 정적 팩토리 메서드 업데이트 (OCR 로그 없이 생성할 때)
    public static Schedule create(User user, String title, LocalDateTime startTime, LocalDateTime endTime, RepeatType repeatType) {
        Schedule schedule = new Schedule();
        schedule.user = user;
        schedule.title = title;
        schedule.startTime = startTime;
        schedule.endTime = endTime;
        schedule.repeatType = repeatType;
        return schedule;
    }

    // 정적 팩토리 메서드 오버로딩 (나중에 OCR을 통해 생성할 때 쓸 생성 메서드)
    // public static Schedule createWithOcr(User user, OcrLog ocrLog, String title, LocalDateTime startTime, LocalDateTime endTime, String repeatType) {
    //     Schedule schedule = Schedule.create(user, title, startTime, endTime, repeatType);
    //     schedule.ocrLog = ocrLog;
    //     return schedule;
    // }

    public void update(String title, LocalDateTime startTime, LocalDateTime endTime, RepeatType repeatType) {
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
    }

    public void delete() {
        this.isDeleted = true;
    }
}
