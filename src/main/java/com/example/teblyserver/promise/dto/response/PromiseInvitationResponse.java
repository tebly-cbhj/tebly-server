package com.example.teblyserver.promise.dto.response;

import com.example.teblyserver.promise.domain.Promise;
import com.example.teblyserver.promise.domain.PromiseMember;
import com.example.teblyserver.promise.domain.PromiseMemberStatus;
import com.example.teblyserver.promise.dto.internal.BusyScheduleTimeRange;
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
        PromiseMemberStatus myStatus,

        // 결정이(Decision Helper)가 생성해 보낸 초대인지 여부
        boolean isFromDecisionBot,

        // 초대받은 사람(나)이 약속 시간에 이미 겹치는 일정이 있는지
        boolean hasScheduleConflict,

        // 겹치는 일정의 카테고리 ID (충돌 없으면 null)
        Long conflictingCategoryId,

        // 겹치는 일정의 카테고리명 (충돌 없으면 null).
        // 중요도 높은 카테고리 충돌이 하나라도 있으면 그 카테고리를 대표로 내려준다.
        String conflictingCategoryName,

        // 겹치는 일정의 제목 (충돌 없으면 null) — "OO 일정이 있는데 조정 가능할까요?" 문구용
        String conflictingScheduleTitle
) {

    // PENDING 상태인 PromiseMember를 초대장 화면용 DTO로 변환
    public static PromiseInvitationResponse from(
            PromiseMember promiseMember,
            Category myCategory,
            BusyScheduleTimeRange representativeConflict
    ) {
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
                promiseMember.getStatus(),

                promise.isCreatedByDecisionHelper(),

                representativeConflict != null,
                representativeConflict != null ? representativeConflict.categoryId() : null,
                representativeConflict != null ? representativeConflict.categoryName() : null,
                representativeConflict != null ? representativeConflict.title() : null
        );
    }
}
