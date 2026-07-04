package com.example.teblyserver.promise.domain;

import com.example.teblyserver.auth.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "promise_member")
public class PromiseMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promise_id", nullable = false)
    private Promise promise;

    // 참석, 불참, 미응답 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromiseMemberStatus status;

    // 마지막으로 '콕 찌르기' 알림을 받은 시간 (도배 방지용)
    @Column(name = "last_poked_at")
    private LocalDateTime lastPokedAt;

    // 정적 팩토리 메서드 (방 멤버들을 약속에 처음 초대할 때)
    public static PromiseMember create(User user, Promise promise) {
        PromiseMember member = new PromiseMember();
        member.user = user;
        member.promise = promise;
        member.status = PromiseMemberStatus.PENDING; // 처음 초대받으면 무조건 '미응답'
        return member;
    }

    // 초대 수락/거절 메서드
    public void updateStatus(PromiseMemberStatus status) {
        this.status = status;
    }

    // 콕 찌르기 시간 갱신 메서드
    public void updateLastPokedAt(LocalDateTime pokedAt) {
        this.lastPokedAt = pokedAt;
    }
}
