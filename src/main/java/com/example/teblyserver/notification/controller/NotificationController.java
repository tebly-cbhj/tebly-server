package com.example.teblyserver.notification.controller;

import com.example.teblyserver.notification.domain.Notification;
import com.example.teblyserver.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 초대 알림 목록 조회
    @GetMapping("/invitation")
    public ResponseEntity<List<Notification>> getInvitationNotifications(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(notificationService.getInvitationNotifications(userId));
    }

    // 일반 알림 목록 조회
    @GetMapping("/common")
    public ResponseEntity<List<Notification>> getCommonNotifications(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(notificationService.getCommonNotifications(userId));
    }

    // 개별 읽음 처리
    @PatchMapping("/common/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}