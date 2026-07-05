package com.example.teblyserver.chat.domain;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.room.domain.Room;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 500)
    private String content;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    // 정적 팩토리 메서드
    public static ChatMessage create(Room room, User sender, String content) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.room = room;
        chatMessage.sender = sender;
        chatMessage.content = content;
        return chatMessage;
    }
}