package com.example.teblyserver.promise.repository;

import com.example.teblyserver.promise.domain.Promise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromiseRepository extends JpaRepository<Promise, Long> {

    // 내가 만든 약속 목록 조회
    @Query("""
            select distinct p
            from Promise p
            join fetch p.sender
            left join fetch p.members pm
            left join fetch pm.user
            where p.room.id = :roomId
              and p.sender.id = :userId
              and p.isDeleted = false
            order by p.proposeStartDate desc, p.id desc
            """)
    List<Promise> findMyPromisesInRoom(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId
    );

    // 내가 초대받은 약속 목록 조회
    @Query("""
            select distinct p
            from Promise p
            join fetch p.sender
            join p.members myMember
            left join fetch p.members pm
            left join fetch pm.user
            where p.room.id = :roomId
              and myMember.user.id = :userId
              and p.sender.id <> :userId
              and p.isDeleted = false
            order by p.proposeStartDate desc, p.id desc
            """)
    List<Promise> findInvitedPromisesInRoom(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId
    );

    // 약속 상세 조회에 필요한 연관 엔티티들을 한 번에 조회
    @Query("""
            select distinct p
            from Promise p
            join fetch p.room
            join fetch p.sender
            left join fetch p.category
            left join fetch p.members pm
            left join fetch pm.user
            where p.id = :promiseId
              and p.isDeleted = false
            """)
    Optional<Promise> findDetailById(@Param("promiseId") Long promiseId);
}
