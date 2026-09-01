package com.example.teblyserver.room.domain;

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
@Table(name = "room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 100)
    private String description;

    @Column(name = "image")
    private String imageUrl;

    @Column(nullable = false)
    private boolean isDeleted = false;

    // 🔗 [연관관계] 방과 방 멤버들 (1 : N)
    // 방이 삭제되면 엮여있는 멤버 데이터도 함께 처리되도록 Cascade 설정
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoomMember> members = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 정적 팩토리 메서드
    public static Room create(String name, String description, String imageUrl) {
        Room room = new Room();
        room.name = name;
        room.description = description; // 추가
        room.imageUrl = imageUrl;
        return room;
    }

    public void updateInfo(String name, String description, String imageUrl) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
        if (description != null) {
            this.description = description;
        }
    }

    public void delete() {
        this.isDeleted = true;

        for (RoomMember member : this.members) {
            member.delete();
        }
    }
}
