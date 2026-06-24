package com.example.teblyserver.promise.repository;

import com.example.teblyserver.promise.domain.PromiseMember;
import com.example.teblyserver.promise.domain.PromiseMemberStatus;
import com.example.teblyserver.promise.domain.PromiseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromiseMemberRepository extends JpaRepository<PromiseMember, Long> {

    Optional<PromiseMember> findByPromiseIdAndUserId(Long promiseId, Long userId);

    // 내가 아직 응답하지 않은 약속 초대장 목록 조회
    // 알림/초대장 화면 - 아직 응답하지 않은 약속 초대 목록 조회
    @Query("""
            select pm
            from PromiseMember pm
            join fetch pm.promise p
            join fetch p.room
            where pm.user.id = :userId
              and pm.status = :memberStatus
              and p.status = :promiseStatus
              and p.isDeleted = false
            order by p.startTime asc, p.id desc
            """)
    List<PromiseMember> findPendingInvitationsByUserId(
            @Param("userId") Long userId,
            @Param("memberStatus") PromiseMemberStatus memberStatus,
            @Param("promiseStatus") PromiseStatus promiseStatus
    );
}
