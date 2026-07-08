package com.example.teblyserver.notification.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.notification.domain.Notification;
import com.example.teblyserver.notification.domain.NotificationType;
import com.example.teblyserver.notification.dto.NotificationResponse;
import com.example.teblyserver.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // 기존 호출부 호환용 (필드 없이 알림 보낼 때)
    @Transactional
    public void send(User receiver, NotificationType type, String title, String content, String redirectPath) {
        send(receiver, type, title, content, redirectPath, null, null, null, null, null, null);
    }

    // 알림 생성
    @Transactional
    public void send(User receiver, NotificationType type, String title, String content, String redirectPath,
                     Long categoryId, Long scheduleId, String scheduleName,
                     Long roomId, String roomName, LocalDateTime targetTime) {

        Notification notification = Notification.builder()
                .user(receiver)
                .type(type)
                .title(title)
                .content(content)
                .redirectPath(redirectPath)
                .categoryId(categoryId)
                .scheduleId(scheduleId)
                .scheduleName(scheduleName)
                .roomId(roomId)
                .roomName(roomName)
                .targetTime(targetTime)
                .build();

        notificationRepository.save(notification);
    }

    // 초대 알림 목록 조회
    public List<NotificationResponse> getInvitationNotifications(Long userId) {
        return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, NotificationType.INVITATION)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    // 일반 알림 목록 조회
    public List<NotificationResponse> getCommonNotifications(Long userId) {
        return notificationRepository.findByUserIdAndTypeNotOrderByCreatedAtDesc(userId, NotificationType.INVITATION)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    // 개별 읽음 처리
    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
    }
}