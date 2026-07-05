package com.example.teblyserver.chat.dto;

public record ChatMessageRequest(
        Long roomId,
        String content
) {}