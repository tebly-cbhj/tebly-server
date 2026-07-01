package com.example.teblyserver.notification.controller;

import com.example.teblyserver.notification.domain.Notification;
import com.example.teblyserver.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.example.teblyserver.notification.dto.NotificationInvitationResponse;
import com.example.teblyserver.promise.dto.response.PromiseInvitationResponse;
import com.example.teblyserver.promise.service.PromiseService;
import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final PromiseService promiseService;

    // 초대 알림 목록 조회
    @GetMapping("/invitation")
    public ResponseEntity<NotificationInvitationResponse> getInvitationNotifications(
            @AuthenticationPrincipal Long userId) {

        List<Notification> roomInvitations = notificationService.getInvitationNotifications(userId);
        List<PromiseInvitationResponse> promiseInvitations = promiseService.getPendingInvitations(userId);

        return ResponseEntity.ok(new NotificationInvitationResponse(roomInvitations, promiseInvitations));
    }

    // 일반 알림 목록 조회
    @GetMapping("/common")
    public ResponseEntity<List<Notification>> getCommonNotifications(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(notificationService.getCommonNotifications(userId));
    }

    // 개별 읽음 처리
    @PatchMapping("/common/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}