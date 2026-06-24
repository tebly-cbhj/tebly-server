package com.example.teblyserver.promise.dto.request;

import com.example.teblyserver.promise.domain.PromiseMemberStatus;
import jakarta.validation.constraints.NotNull;

public record PromiseInvitationRespondRequest(
        @NotNull(message = "응답 상태는 필수입니다.")
        PromiseMemberStatus status
) {
}
