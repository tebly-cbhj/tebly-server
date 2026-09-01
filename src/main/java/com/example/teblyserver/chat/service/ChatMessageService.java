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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    // chat_message.content 컬럼 길이 제한 (ChatMessage 엔티티 @Column(length = 500)과 동일)
    private static final int CONTENT_MAX_LENGTH = 500;

    private final ChatMessageRepository chatMessageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

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

    /**
     * 결정이(Decision Helper)가 약속을 자동으로 생성했을 때, 그 선택 사유를 방 채팅에 공유한다.
     * 실제로 채팅을 입력한 사람은 없으므로 결정이를 호출한 유저 명의로 남기지만,
     * summary 문구 자체가 "결정이의 추천시간은..."으로 시작해 자동 생성된 메시지임을 알 수 있다.
     */
    @Transactional
    public ChatMessageResponse postDecisionSummary(Long roomId, Long senderId, String summary) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String content = truncateToContentLimit(summary);

        ChatMessage chatMessage = ChatMessage.create(room, sender, content);
        ChatMessage saved = chatMessageRepository.save(chatMessage);

        ChatMessageResponse response = ChatMessageResponse.from(saved);
        messagingTemplate.convertAndSend("/topic/chat/room/" + roomId, response);

        return response;
    }

    private String truncateToContentLimit(String content) {
        if (content.length() <= CONTENT_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, CONTENT_MAX_LENGTH);
    }
}