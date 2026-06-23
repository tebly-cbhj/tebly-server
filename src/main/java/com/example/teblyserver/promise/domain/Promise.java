package com.example.teblyserver.promise.domain;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.schedule.domain.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "promise")
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

    // API 명세서의 confirmed_at (시간이 확정되면 값이 채워짐)
    @Column(name = "confirmed_time")
    private LocalDateTime confirmedTime;

    @Column(length = 50)
    private String location; // 확정된 장소(단순 문자열)

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
    public static Promise create(Room room, User sender, Category category, String title,
                                 String comment, LocalDate startDate, LocalDate endDate,
                                 Integer minDuration) { // <- 여기 추가됨!
        Promise promise = new Promise();
        promise.room = room;
        promise.sender = sender;
        promise.category = category;
        promise.title = title;
        promise.comment = comment;
        promise.proposeStartDate = startDate;
        promise.proposeEndDate = endDate;
        promise.minDuration = minDuration; // <- 여기 추가됨!
        promise.status = PromiseStatus.PENDING;
        return promise;
    }

    // 약속 확정/수정 메서드
    public void confirm(LocalDateTime confirmedTime, String location) {
        this.confirmedTime = confirmedTime;
        this.location = location;
        this.status = PromiseStatus.CONFIRMED; // 상태도 확정으로 자동 변경
    }

    // 약속 삭제 메서드 (Soft Delete)
    public void delete() {
        this.isDeleted = true;
        // 필요하다면 this.members.forEach(PromiseMember::delete); 로 연쇄 삭제 추가
    }
}
