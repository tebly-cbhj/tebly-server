package com.example.teblyserver.promise.dto.response;

import com.example.teblyserver.promise.domain.Promise;
import com.example.teblyserver.promise.domain.PromiseMember;
import com.example.teblyserver.promise.domain.PromiseMemberStatus;
import com.example.teblyserver.schedule.domain.Category;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PromiseInvitationResponse(
        Long promiseId,
        String title,

        LocalDate proposeStartDate,
        LocalDate proposeEndDate,

        LocalDateTime startTime,
        LocalDateTime endTime,

        String location,

        Long myCategoryId,
        String categoryName,
        String categoryIconUrl,

        String roomName,
        PromiseMemberStatus myStatus
) {

    // PENDING 상태인 PromiseMember를 초대장 화면용 DTO로 변환
    public static PromiseInvitationResponse from(PromiseMember promiseMember, Category myCategory) {
        Promise promise = promiseMember.getPromise();

        return new PromiseInvitationResponse(
                promise.getId(),
                promise.getTitle(),

                promise.getProposeStartDate(),
                promise.getProposeEndDate(),

                promise.getStartTime(),
                promise.getEndTime(),

                promise.getLocation(),

                myCategory != null ? myCategory.getId() : null,
                promise.getCategory().getName(),
                myCategory != null ? myCategory.getIcon() : promise.getCategory().getIcon(),

                promise.getRoom().getName(),
                promiseMember.getStatus()
        );
    }
}