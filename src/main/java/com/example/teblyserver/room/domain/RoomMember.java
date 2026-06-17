package com.example.teblyserver.room.domain;

import com.example.teblyserver.auth.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔗 [연관관계] 멤버가 속한 방 (N : 1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // 🔗 [연관관계] 방에 속한 유저 (N : 1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "invite_status", nullable = false, length = 20)
    private InviteStatus inviteStatus;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @JoinColumn(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    // 정적 팩토리 메서드 (객체 생성과 동시에 양방향 연관관계 세팅)
    public static RoomMember create(Room room, User user, RoomRole role, InviteStatus inviteStatus) {
        RoomMember roomMember = new RoomMember();
        roomMember.room = room;
        roomMember.user = user;
        roomMember.role = role;
        roomMember.inviteStatus = inviteStatus;

        // 양방향 연관관계 편의 로직: 방 객체 내부의 members 리스트에도 나 자신을 쏙 넣어줌
        room.getMembers().add(roomMember);

        return roomMember;
    }

    // 비즈니스 로직
    public void acceptInvitation() {
        this.inviteStatus = InviteStatus.ACCEPTED;
    }

    public void rejectInvitation() {
        this.inviteStatus = InviteStatus.REJECTED;
    }

    public void delegateHost() {
        this.role = RoomRole.HOST;
    }

    public void delete() {
        this.isDeleted = true;
    }
}
