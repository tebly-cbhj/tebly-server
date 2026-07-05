package com.example.teblyserver.chat.controller;

import com.example.teblyserver.chat.dto.ChatMessageRequest;
import com.example.teblyserver.chat.dto.ChatMessageResponse;
import com.example.teblyserver.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/send")
    public void sendMessage(ChatMessageRequest request,
                            @Header("simpSessionAttributes") Map<String, Object> sessionAttributes) {

        Long senderId = (Long) sessionAttributes.get("userId");

        if (senderId == null) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }

        ChatMessageResponse response = chatMessageService.saveMessage(request, senderId);
        messagingTemplate.convertAndSend("/topic/chat/room/" + request.roomId(), response);
    }

    @GetMapping("/chat/rooms/{roomId}/messages")
    public List<ChatMessageResponse> getMessages(@PathVariable Long roomId) {
        return chatMessageService.getMessages(roomId);
    }
}