package com.example.teblyserver.notification.repository;

import com.example.teblyserver.notification.domain.Notification;
import com.example.teblyserver.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, NotificationType type);
    List<Notification> findByUserIdAndTypeNotOrderByCreatedAtDesc(Long userId, NotificationType type);
}