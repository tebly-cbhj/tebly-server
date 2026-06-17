package com.example.teblyserver.room.repository;

import com.example.teblyserver.room.domain.InviteStatus;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.room.domain.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    // 유저 ID와 초대 상태로 검색하여, 연결된 Room 객체들의 리스트를 바로 반환합니다.
    @Query("SELECT rm.room FROM RoomMember rm WHERE rm.user.id = :userId AND rm.inviteStatus = :status AND rm.room.isDeleted = false")
    List<Room> findRoomsByUserIdAndInviteStatus(@Param("userId") Long userId, @Param("status") InviteStatus status);
}
