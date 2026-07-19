package com.example.teblyserver.chat.repository;

import com.example.teblyserver.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 특정 방의 채팅 내역을 시간순으로 조회
    List<ChatMessage> findByRoomIdOrderBySentAtAsc(Long roomId);

    /**
     * 여러 방의 안읽은 메시지 개수를 한 번에 집계
     * - 내가 보낸 메시지는 제외
     * - lastReadAt이 없으면 방 참여 시점(createdAt) 기준
     * 반환: [roomId, count] 배열 목록 (안읽은 메시지가 없는 방은 결과에서 빠짐)
     */
    @Query("SELECT m.room.id, COUNT(m) FROM ChatMessage m " +
            "JOIN RoomMember rm ON rm.room.id = m.room.id AND rm.user.id = :userId " +
            "WHERE m.room.id IN :roomIds " +
            "AND m.sender.id <> :userId " +
            "AND m.sentAt > COALESCE(rm.lastReadAt, rm.createdAt) " +
            "GROUP BY m.room.id")
    List<Object[]> countUnreadByRooms(@Param("userId") Long userId,
                                      @Param("roomIds") List<Long> roomIds);
}