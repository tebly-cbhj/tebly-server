package com.example.teblyserver.chat.dto;

import com.example.teblyserver.chat.domain.ChatMessage;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long roomId,
        Long senderId,
        String senderNickname,
        String senderProfileImageUrl,
        String content,
        LocalDateTime sentAt
) {
    public static ChatMessageResponse from(ChatMessage chatMessage) {
        return new ChatMessageResponse(
                chatMessage.getId(),
                chatMessage.getRoom().getId(),
                chatMessage.getSender().getId(),
                chatMessage.getSender().getNickname(),
                chatMessage.getSender().getProfileImageUrl(),
                chatMessage.getContent(),
                chatMessage.getSentAt()
        );
    }
}