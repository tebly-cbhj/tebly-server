package com.example.teblyserver.friend.repository;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.friend.domain.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    List<Friendship> findByRequester(User requester);
    List<Friendship> findByReceiver(User receiver);
    Optional<Friendship> findByRequesterAndReceiver(User requester, User receiver);
    boolean existsByRequesterAndReceiver(User requester, User receiver);
}