package com.example.teblyserver.notification.dto;

import com.example.teblyserver.notification.domain.Notification;
import com.example.teblyserver.notification.domain.NotificationType;

import java.time.Duration;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String content,
        boolean isRead,
        String redirectPath,
        Long categoryId,
        Long scheduleId,
        String scheduleName,
        Long roomId,
        String roomName,
        Long timeLeftMinutes, // 남은 시간(분). 대상 시각 없거나 이미 지났으면 0
        Long senderId, // 알림을 유발한 행위자(예: 콕찌르기를 보낸 사람). 없으면 null
        String senderNickname,
        String senderProfileImageUrl,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        Long timeLeft = null;
        if (n.getTargetTime() != null) {
            long minutes = Duration.between(LocalDateTime.now(), n.getTargetTime()).toMinutes();
            timeLeft = Math.max(minutes, 0);
        }

        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getContent(),
                n.isRead(),
                n.getRedirectPath(),
                n.getCategoryId(),
                n.getScheduleId(),
                n.getScheduleName(),
                n.getRoomId(),
                n.getRoomName(),
                timeLeft,
                n.getSenderId(),
                n.getSenderNickname(),
                n.getSenderProfileImageUrl(),
                n.getCreatedAt()
        );
    }
}