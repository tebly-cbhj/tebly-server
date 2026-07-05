package com.example.teblyserver.chat.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.chat.domain.ChatMessage;
import com.example.teblyserver.chat.dto.ChatMessageRequest;
import com.example.teblyserver.chat.dto.ChatMessageResponse;
import com.example.teblyserver.chat.repository.ChatMessageRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.room.domain.InviteStatus;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.room.repository.RoomMemberRepository;
import com.example.teblyserver.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Transactional
    public ChatMessageResponse saveMessage(ChatMessageRequest request, Long senderId) {
        Long roomId = request.roomId();

        boolean isMember = roomMemberRepository.existsByRoomIdAndUserIdAndInviteStatus(
                roomId, senderId, InviteStatus.ACCEPTED);
        if (!isMember) {
            throw new CustomException(ErrorCode.CHAT_ROOM_MEMBER_FORBIDDEN);
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatMessage chatMessage = ChatMessage.create(room, sender, request.content());
        ChatMessage saved = chatMessageRepository.save(chatMessage);

        return ChatMessageResponse.from(saved);
    }

    public List<ChatMessageResponse> getMessages(Long roomId) {
        return chatMessageRepository.findByRoomIdOrderBySentAtAsc(roomId)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }
}