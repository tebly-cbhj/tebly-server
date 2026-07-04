package com.example.teblyserver.notification.dto;

import com.example.teblyserver.notification.domain.Notification;
import com.example.teblyserver.promise.dto.response.PromiseInvitationResponse;

import java.util.List;

public record NotificationInvitationResponse(
        List<Notification> roomInvitations,
        List<PromiseInvitationResponse> promiseInvitations
) {}
