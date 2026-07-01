package com.example.teblyserver.notification.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.notification.domain.Notification;
import com.example.teblyserver.notification.domain.NotificationType;
import com.example.teblyserver.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // 알림 생성 (내부용 - 다른 서비스에서 호출)
    @Transactional
    public void send(User receiver, NotificationType type, String title, String content, String redirectPath) {
        Notification notification = Notification.builder()
                .user(receiver)
                .type(type)
                .title(title)
                .content(content)
                .redirectPath(redirectPath)
                .build();

        notificationRepository.save(notification);
    }

    // 초대 알림 목록 조회
    public List<Notification> getInvitationNotifications(Long userId) {
        return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, NotificationType.INVITATION);
    }

    // 일반 알림 목록 조회
    public List<Notification> getCommonNotifications(Long userId) {
        return notificationRepository.findByUserIdAndTypeNotOrderByCreatedAtDesc(userId, NotificationType.INVITATION);
    }

    // 개별 읽음 처리
    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
    }
}