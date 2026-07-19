package com.example.teblyserver.friend.domain;

import com.example.teblyserver.auth.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "friendship")
@Getter
@NoArgsConstructor
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    // 즐겨찾기는 한 행을 공유하는 두 유저 각자의 설정이므로 방향별로 저장한다.
    // (requester가 receiver를 즐겨찾기해도 receiver 화면에는 영향 없음)
    // columnDefinition의 default는 기존 데이터가 있는 운영 DB에 not null 컬럼을 추가하기 위해 필요하다.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean requesterFavorite = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean receiverFavorite = false;

    public static Friendship create(User requester, User receiver) {
        Friendship friendship = new Friendship();
        friendship.requester = requester;
        friendship.receiver = receiver;
        return friendship;
    }

    // user 입장에서 상대방을 즐겨찾기했는지
    public boolean isFavoriteFor(User user) {
        return requester.getId().equals(user.getId()) ? requesterFavorite : receiverFavorite;
    }

    // user 입장에서의 즐겨찾기 여부를 설정
    public void updateFavorite(User user, boolean favorite) {
        if (requester.getId().equals(user.getId())) {
            this.requesterFavorite = favorite;
        } else {
            this.receiverFavorite = favorite;
        }
    }
}