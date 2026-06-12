package com.example.teblyserver.notification.repository;

import com.example.teblyserver.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}