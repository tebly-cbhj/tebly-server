package com.example.teblyserver.promise.domain;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.schedule.domain.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "promise")
@SQLRestriction("is_deleted= false")
public class Promise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 방(Room)에서 만들어진 약속인지 연결 (다대일 양방향)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 50)
    private String title; // 약속명

    @Column(length = 255)
    private String comment;

    @Column(name = "propose_start_date")
    private LocalDate proposeStartDate;

    @Column(name = "propose_end_date")
    private LocalDate proposeEndDate;

    /**
     * 약속 시간
     *
     * status == PENDING   → 제안된 약속 시간
     * status == CONFIRMED → 확정된 약속 시간
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(length = 50)
    private String location; // 확정된 장소(단순 문자열)

    // 알림 시간
    // 예: 5분 전 = 5, 10분 전 = 10, 1시간 전 = 60, 1일 전 = 1440
    // 다중 설정
    @ElementCollection
    @CollectionTable(
            name = "promise_notification_lead_minutes",
            joinColumns = @JoinColumn(name = "promise_id")
    )
    @Column(name = "lead_minutes")
    private List<Integer> notificationLeadMinutes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromiseStatus status;

    @Column(nullable = false)
    private boolean isDeleted = false;

    // 약속에 참여하는 멤버 목록
    @OneToMany(mappedBy = "promise", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PromiseMember> members = new ArrayList<>();

    // 최소 시간 설정 (분 단위 저장, 예: 2시간 = 120)
    @Column(name = "min_duration", nullable = false)
    private Integer minDuration;

    // 정적 팩토리 메서드 (처음 약속을 생성할 때)
    public static Promise create(
            Room room, User sender, Category category, String title, String comment,
            LocalDate proposeStartDate, LocalDate proposeEndDate,
            LocalDateTime startTime, LocalDateTime endTime,
            String location, List<Integer> notificationLeadMinutes, Integer minDuration
    ) {
        Promise promise = new Promise();
        promise.room = room;
        promise.sender = sender;
        promise.category = category;
        promise.title = title;
        promise.comment = comment;
        promise.proposeStartDate = proposeStartDate;
        promise.proposeEndDate = proposeEndDate;
        promise.startTime = startTime;
        promise.endTime = endTime;
        promise.location = location;
        promise.notificationLeadMinutes = notificationLeadMinutes != null
                ? new ArrayList<>(notificationLeadMinutes) : new ArrayList<>();
        promise.minDuration = minDuration;
        promise.status = PromiseStatus.PENDING;
        return promise;
    }

    public void update(
            Category category, String title, String comment,
            LocalDate proposeStartDate, LocalDate proposeEndDate,
            LocalDateTime startTime, LocalDateTime endTime,
            String location, List<Integer> notificationLeadMinutes, Integer minDuration
    ) {
        this.category = category;
        this.title = title;
        this.comment = comment;
        this.proposeStartDate = proposeStartDate;
        this.proposeEndDate = proposeEndDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.notificationLeadMinutes = notificationLeadMinutes != null
                ? new ArrayList<>(notificationLeadMinutes) : new ArrayList<>();
        this.minDuration = minDuration;
    }

    public void confirm() {
        this.status = PromiseStatus.CONFIRMED;
    }


    // 약속 삭제 메서드 (Soft Delete)
    public void delete() {
        this.isDeleted = true;
        this.status = PromiseStatus.CANCELED;
        // 필요하다면 this.members.forEach(PromiseMember::delete); 로 연쇄 삭제 추가
    }
}
