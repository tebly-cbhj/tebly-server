package com.example.teblyserver.notification.dto;

import com.example.teblyserver.promise.dto.response.PromiseInvitationResponse;

import java.util.List;

public record NotificationInvitationResponse(
        List<NotificationResponse> roomInvitations,
        List<PromiseInvitationResponse> promiseInvitations
) {}