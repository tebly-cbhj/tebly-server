package com.example.teblyserver.notification.controller;

import com.example.teblyserver.notification.dto.NotificationInvitationResponse;
import com.example.teblyserver.notification.dto.NotificationResponse;
import com.example.teblyserver.notification.service.NotificationService;
import com.example.teblyserver.promise.dto.response.PromiseInvitationResponse;
import com.example.teblyserver.promise.service.PromiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final PromiseService promiseService;

    @GetMapping("/invitation")
    public ResponseEntity<NotificationInvitationResponse> getInvitationNotifications(
            @AuthenticationPrincipal Long userId) {

        List<NotificationResponse> roomInvitations = notificationService.getInvitationNotifications(userId);
        List<PromiseInvitationResponse> promiseInvitations = promiseService.getPendingInvitations(userId);

        return ResponseEntity.ok(new NotificationInvitationResponse(roomInvitations, promiseInvitations));
    }

    @GetMapping("/common")
    public ResponseEntity<List<NotificationResponse>> getCommonNotifications(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(notificationService.getCommonNotifications(userId));
    }

    @PatchMapping("/common/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}