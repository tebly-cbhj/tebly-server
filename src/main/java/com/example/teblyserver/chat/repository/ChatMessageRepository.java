package com.example.teblyserver.chat.repository;

import com.example.teblyserver.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 특정 방의 채팅 내역을 시간순으로 조회 (나중에 내역 조회 기능 만들 때 사용)
    List<ChatMessage> findByRoomIdOrderBySentAtAsc(Long roomId);
}