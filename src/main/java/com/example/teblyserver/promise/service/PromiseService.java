package com.example.teblyserver.promise.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.notification.domain.NotificationType;
import com.example.teblyserver.notification.service.NotificationService;
import com.example.teblyserver.promise.domain.Promise;
import com.example.teblyserver.promise.domain.PromiseMember;
import com.example.teblyserver.promise.domain.PromiseMemberStatus;
import com.example.teblyserver.promise.domain.PromiseStatus;
import com.example.teblyserver.promise.dto.internal.BusyScheduleTimeRange;
import com.example.teblyserver.promise.dto.request.*;
import com.example.teblyserver.promise.dto.response.PromiseDetailResponse;
import com.example.teblyserver.promise.dto.response.PromiseInvitationResponse;
import com.example.teblyserver.promise.dto.response.PromisePokeResponse;
import com.example.teblyserver.promise.dto.response.PromiseTimeRecommendationResponse;
import com.example.teblyserver.promise.repository.PromiseMemberRepository;
import com.example.teblyserver.promise.repository.PromiseRepository;
import com.example.teblyserver.room.domain.InviteStatus;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.room.domain.RoomMember;
import com.example.teblyserver.room.repository.RoomRepository;
import com.example.teblyserver.schedule.domain.Category;
import com.example.teblyserver.schedule.repository.CategoryRepository;
import com.example.teblyserver.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PromiseService {

    // 충돌 일정이 이 카테고리들 뿐이면 "조정 가능한" 낮은 중요도로 간주한다.
    // (실제 기본 카테고리 시드 데이터 철자가 "자기개발"이라 그대로 맞춤 — "자기계발"은 오탈자로 보임)
    private static final Set<String> LOW_IMPORTANCE_CATEGORY_NAMES = Set.of("여가", "자기개발");

    private final PromiseRepository promiseRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PromiseMemberRepository promiseMemberRepository;
    private final ScheduleService scheduleService;
    private final PromiseRecommendationService promiseRecommendationService;
    private final NotificationService notificationService;

    /**
     * 새로운 약속 생성 (일반적인 경로 — 결정이를 거치지 않은 생성)
     * 초대 메시지는 멤버 상황과 무관하게 항상 동일한 기본 문구를 사용한다.
     */
    @Transactional
    public Long createPromise(Long userId, Long roomId, PromiseCreateRequest request) {
        return createPromiseInternal(userId, roomId, request, this::buildStandardInvitationMessage, false);
    }

    /**
     * 결정이(Decision Helper)가 분석한 결과를 바탕으로 약속을 생성한다.
     * 이 경로에서만 멤버 상황별로 다른 초대 메시지를 보낸다 (buildInvitationMessage 참고).
     */
    @Transactional
    public Long createPromiseFromDecisionHelper(Long userId, Long roomId, PromiseCreateRequest request) {
        return createPromiseInternal(userId, roomId, request, this::buildInvitationMessage, true);
    }

    private Long createPromiseInternal(
            Long userId,
            Long roomId,
            PromiseCreateRequest request,
            BiFunction<Promise, User, String> invitationMessageBuilder,
            boolean fromDecisionHelper
    ) {

        // 1. 필요한 엔티티들 조회 (유저, 방)
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 권한 검증: 약속을 제안하는 유저가 이 방의 수락된 멤버가 맞는지 확인
        boolean isRoomMember = room.getMembers().stream()
                .anyMatch(rm -> rm.getUser().getId().equals(userId) && rm.getInviteStatus() == InviteStatus.ACCEPTED);
        if (!isRoomMember) {
            throw new CustomException(ErrorCode.PROMISE_FORBIDDEN);
        }

        validatePromiseTime(
                request.proposeStartDate(),
                request.proposeEndDate(),
                request.startTime(),
                request.endTime(),
                request.minDuration()
        );

        // 3. 카테고리 조회 (선택 사항이므로 null 체크)
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        // 약속 생성자가 자기 카테고리만 선택할 수 있도록 검증
        if (!category.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.CATEGORY_FORBIDDEN);
        }
        // 일단은 디폴트 카테고리만 선택 가능하도록 제한
        if (!category.isDefault()) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_CATEGORY);
        }


        // 4. 약속(Promise) 엔티티 생성
        Promise promise = Promise.create(
                room, sender, category,
                request.title(), request.comment(),
                request.proposeStartDate(), request.proposeEndDate(),
                request.startTime(), request.endTime(),
                request.location(), request.notificationLeadMinutes(), request.minDuration()
        );

        if (fromDecisionHelper) {
            promise.markCreatedByDecisionHelper();
        }

        // 약속 생성자는 항상 PromiseMember에 포함하고 ACCEPTED 처리
        PromiseMember senderPromiseMember = PromiseMember.create(sender, promise);
        senderPromiseMember.updateStatus(PromiseMemberStatus.ACCEPTED);
        promise.getMembers().add(senderPromiseMember);

        // 프론트에서 선택한 초대 대상 userId 중복 제거
        Set<Long> inviteeIds = new LinkedHashSet<>(request.inviteeIds());
        // 생성자가 inviteeIds에 들어와도 중복 생성되지 않도록 제거
        inviteeIds.remove(userId);


        for (Long inviteeId : inviteeIds) {
            RoomMember roomMember = room.getMembers().stream()
                    .filter(rm -> rm.getUser().getId().equals(inviteeId))
                    .filter(rm -> rm.getInviteStatus() == InviteStatus.ACCEPTED)
                    .findFirst()
                    .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_INVITEE_FORBIDDEN));

            PromiseMember inviteePromiseMember = PromiseMember.create(roomMember.getUser(), promise);
            promise.getMembers().add(inviteePromiseMember);
        }

        // 6. DB 저장
        Promise savedPromise = promiseRepository.save(promise);

        // 7. 초대된 멤버들에게 즉시 약속 초대 알림 발송 (약속 생성자 본인 제외)
        for (PromiseMember member : savedPromise.getMembers()) {
            if (!member.getUser().getId().equals(userId)) {
                notificationService.send(
                        member.getUser(),
                        NotificationType.INVITATION,
                        savedPromise.getTitle(),
                        invitationMessageBuilder.apply(savedPromise, member.getUser()),
                        "/promises/" + savedPromise.getId(),
                        savedPromise.getCategory().getId(),   // categoryId
                        savedPromise.getId(),                  // scheduleId (약속 ID)
                        savedPromise.getTitle(),                // scheduleName
                        room.getId(),                           // roomId
                        room.getName(),                         // roomName
                        savedPromise.getStartTime(),             // targetTime
                        sender.getId(),
                        sender.getNickname(),
                        sender.getProfileImageUrl()
                );
            }
        }

        return savedPromise.getId();
    }

    // 결정이를 거치지 않은 일반 약속 생성 시 사용하는 기본 초대 메시지
    private String buildStandardInvitationMessage(Promise promise, User invitee) {
        return promise.getSender().getNickname() + "님이 약속을 제안했어요!";
    }

    /**
     * 결정이(Decision Helper)를 통해 생성된 약속에서만 사용하는, 멤버의 상황에 따라 다른 초대 메시지.
     *
     * 1. 해당 시간에 충돌하는 일정이 전혀 없으면 → 일반 제안 메시지
     * 2. 충돌하는 일정이 있지만 전부 낮은 중요도 카테고리(여가/자기개발 등)라면 → 조정 가능한지 물어보는 메시지
     * 3. 그 외(충돌 일정이 하나라도 낮은 중요도가 아니면) → 참석이 어려울 수 있다는 메시지
     *
     * 세 경우 모두 PromiseMember는 동일하게 PENDING으로 생성되어, 수락/거절 자체는 똑같이 가능하다.
     * 메시지만 상황에 맞게 달라진다.
     */
    private String buildInvitationMessage(Promise promise, User invitee) {
        List<BusyScheduleTimeRange> conflicts = promiseRecommendationService.findExpandedSchedulesInWindow(
                List.of(invitee.getId()),
                promise.getStartTime().toLocalDate(),
                promise.getStartTime(),
                promise.getEndTime()
        );

        if (conflicts.isEmpty()) {
            return "이 시간으로 약속이 제안되었어요";
        }

        boolean allConflictsLowImportance = conflicts.stream()
                .allMatch(conflict -> LOW_IMPORTANCE_CATEGORY_NAMES.contains(conflict.categoryName()));

        if (allConflictsLowImportance) {
            String conflictTitle = conflicts.get(0).title();
            return "이 시간에 '" + conflictTitle + "' 일정이 있는데 조정이 가능할까요?";
        }

        return "이 시간은 참석이 어려울 수 있어요";
    }

    /**
     * 추천 시간 선택 기반 약속 생성
     *
     * 흐름:
     * 1. 프론트가 선택한 추천 시간(selectedStartTime/selectedEndTime)을 보냄
     * 2. 백엔드가 추천 알고리즘을 다시 실행
     * 3. 선택한 시간이 실제 추천 결과에 있는지 확인
     * 4. 해당 추천 결과의 availableMembers만 약속 멤버로 사용
     * 5. 기존 createPromise()를 재사용해서 Promise 생성
     */
    @Transactional
    public Long createPromiseFromRecommendation(
            Long userId,
            Long roomId,
            PromiseCreateFromRecommendationRequest request
    ) {
        // 1. 추천 API에 넣을 요청 DTO를 다시 구성한다.
        PromiseTimeRecommendRequest recommendRequest = new PromiseTimeRecommendRequest(
                request.proposeStartDate(),
                request.proposeEndDate(),
                request.searchStartTime(),
                request.searchEndTime(),
                request.minDuration(),
                request.sortType(),
                request.selectedMemberIds()
        );

        // 2. 현재 시점 기준으로 추천 알고리즘을 다시 실행한다.
        // 이유:
        // - 프론트가 availableMembers를 조작해서 보내는 것을 막기 위함
        // - 추천 조회 이후 누군가 일정을 추가/수정했을 가능성을 반영하기 위함
        List<PromiseTimeRecommendationResponse> recommendations =
                promiseRecommendationService.recommendPromiseTimes(
                        userId,
                        roomId,
                        recommendRequest
                );

        // 3. 프론트가 선택한 시간이 실제 추천 결과 안에 있는지 확인한다.
        PromiseTimeRecommendationResponse selectedRecommendation = recommendations.stream()
                .filter(recommendation ->
                        recommendation.startTime().equals(request.recommendedStartTime())
                                && recommendation.endTime().equals(request.recommendedEndTime())
                )
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PROMISE_TIME));

        // 4. 최종 약속 시간을 결정한다.
        // - selectedStartTime/selectedEndTime 둘 다 null이면 추천 시간 그대로 사용
        // - 둘 다 있으면 사용자가 수정한 시간 사용
        // - 하나만 null이면 잘못된 요청
        SelectedPromiseTime finalSelectedTime = resolveSelectedPromiseTime(request);

        // 5. 최종 선택 시간이 원본 추천 범위 안에 있는지 검증한다.
        validateAdjustedRecommendationTime(
                selectedRecommendation,
                finalSelectedTime.startTime(),
                finalSelectedTime.endTime(),
                request.minDuration()
        );

        // 6. 선택한 추천 시간에서 가능한 멤버만 추출한다.
        // availableMembers에는 생성자 본인도 포함되어 있을 수 있다.
        List<Long> availableMemberIds = selectedRecommendation.availableMembers().stream()
                .map(member -> member.userId())
                .distinct()
                .toList();

        // 7. 생성자가 이 시간에 가능하지 않다면 약속을 만들 수 없다.
        if (!availableMemberIds.contains(userId)) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_MEMBER);
        }

        // 8. PromiseCreateRequest의 inviteeIds에는 생성자를 제외한 가능한 멤버만 넣는다.
        // 생성자는 createPromise() 안에서 자동으로 PromiseMember에 추가되고 ACCEPTED 처리된다.
        List<Long> inviteeIds = availableMemberIds.stream()
                .filter(availableMemberId -> !availableMemberId.equals(userId))
                .toList();

        // 9. 가능한 상대방이 아무도 없으면 약속 생성 불가로 처리한다.
        if (inviteeIds.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_MEMBER);
        }

        // 10. 기존 약속 생성 DTO로 변환한다.
        PromiseCreateRequest createRequest = new PromiseCreateRequest(
                request.title(),
                request.comment(),
                request.categoryId(),
                request.proposeStartDate(),
                request.proposeEndDate(),
                finalSelectedTime.startTime(),
                finalSelectedTime.endTime(),
                request.location(),
                request.notificationLeadMinutes(),
                request.minDuration(),
                inviteeIds
        );

        // 11. 기존 createPromise() 로직 재사용
        return createPromise(userId, roomId, createRequest);
    }

    /**
     * 약속 상세 조회
     *
     * @param userId 현재 로그인한 사용자 ID
     * @param promiseId 조회하려는 약속 ID
     * @return 약속 상세 정보 응답 DTO
     */
    @Transactional(readOnly = true)
    public PromiseDetailResponse getPromiseDetail(Long userId, Long promiseId) {

        // 1. 약속 상세 정보 조회
        // findDetailById()는 Promise뿐만 아니라
        // room, sender, category, members, members.user까지 fetch join으로 함께 조회한다.
        Promise promise = promiseRepository.findDetailById(promiseId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_NOT_FOUND));

        // 2. 권한 검증
        // 약속 상세 정보는 이 약속에 포함된 멤버만 볼 수 있어야 한다.
        // 즉, 현재 로그인한 사용자가 promise.members 안에 있는지 확인한다.
        // 약속 생성 시 방의 ACCEPTED 멤버들을 PromiseMember로 추가하고 있으므로,
        // PromiseMember에 포함되어 있다는 것은 이 약속에 초대된 사용자라는 뜻이다.
        boolean isPromiseMember = promise.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(userId));

        // 3. 약속 멤버가 아니라면 접근 금지 예외 발생
        if (!isPromiseMember) {
            throw new CustomException(ErrorCode.PROMISE_FORBIDDEN);
        }

        // 자신의 카테고리 Id로 변경
        String categoryName = promise.getCategory().getName();
        Category myCategory = categoryRepository
                .findByUserIdAndNameAndIsDefaultTrue(userId, categoryName)
                .orElseThrow(() -> new CustomException(ErrorCode.DEFAULT_CATEGORY_MISSING));

        // 4. 엔티티를 응답 DTO로 변환해서 반환
        return PromiseDetailResponse.from(promise, userId, myCategory.getId());
    }

    /**
     * 약속 수정
     */
    @Transactional
    public Long updatePromise(Long userId, Long promiseId, PromiseUpdateRequest request) {

        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_NOT_FOUND));

        validatePromiseSender(promise, userId);
        validatePromisePending(promise);

        validatePromiseTime(
                request.proposeStartDate(),
                request.proposeEndDate(),
                request.startTime(),
                request.endTime(),
                request.minDuration()
        );

        Category category = promise.getCategory();
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

            if (!category.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.CATEGORY_FORBIDDEN);
            }

            if (!category.isDefault()) {
                throw new CustomException(ErrorCode.INVALID_PROMISE_CATEGORY);
            }
        }

        // 약속 시간이 바뀌었는지 먼저 확인
        boolean isTimeChanged =
                !Objects.equals(promise.getStartTime(), request.startTime())
                        || !Objects.equals(promise.getEndTime(), request.endTime());

        promise.update(
                category,
                request.title(),
                request.comment(),
                request.proposeStartDate(),
                request.proposeEndDate(),
                request.startTime(),
                request.endTime(),
                request.location(),
                request.notificationLeadMinutes(),
                request.minDuration()
        );

        // 시간이 바뀌었다면 기존 수락/거절 상태를 다시 초기화
        if (isTimeChanged) {
            resetMemberStatusesForTimeChange(promise);
        }

        return promise.getId();
    }

    /**
     * 약속 확정
     *
     * startTime/endTime은 이미 약속 생성 또는 수정 시 저장되어 있으므로
     * 확정 시에는 status만 CONFIRMED로 변경한다.
     */
    @Transactional
    public Long confirmPromise(Long userId, Long promiseId) {

        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_NOT_FOUND));

        validatePromiseSender(promise, userId);
        validatePromisePending(promise);

        promise.confirm();

        scheduleService.addPromiseSchedules(promise);

        return promise.getId();
    }

    /**
     * 약속 초대장 수락/거절
     *
     * 약속 상세 화면에서 응답하든,
     * 알림 초대장 화면에서 응답하든,
     * 최종적으로 이 메서드를 사용해서 PromiseMember.status를 변경한다.
     */
    @Transactional
    public void respondInvitation(Long userId, Long promiseId, PromiseInvitationRespondRequest request) {

        // 1. 약속 조회
        Promise promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_NOT_FOUND));

        // 2. 이미 확정되었거나 취소된 약속에는 응답할 수 없음
        if (promise.getStatus() != PromiseStatus.PENDING) {
            throw new CustomException(ErrorCode.PROMISE_ALREADY_CLOSED);
        }

        // 3. 현재 로그인한 유저에게 온 약속 초대장 조회
        PromiseMember promiseMember = promiseMemberRepository.findByPromiseIdAndUserId(promiseId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_FORBIDDEN));

        // 4. 약속 생성자는 초대장 응답 대상이 아님
        if (promise.getSender().getId().equals(userId)) {
            throw new CustomException(ErrorCode.PROMISE_FORBIDDEN);
        }


        // 5. 초대장 응답 상태 저장
        promiseMember.updateStatus(request.status());
    }


    /**
     * 내가 아직 응답하지 않은 약속 초대장 목록 조회
     * TODO : 알림 컨트롤러랑 연결 - 초대장 화면에 PENDING인 약속 리스트 보여주기
     */
    @Transactional(readOnly = true)
    public List<PromiseInvitationResponse> getPendingInvitations(Long userId) {

        List<PromiseMember> pendingInvitations =
                promiseMemberRepository.findPendingInvitationsByUserId(
                        userId,
                        PromiseMemberStatus.PENDING,
                        PromiseStatus.PENDING
                );

        // 내 기본 카테고리를 한 번에 조회해서 이름 -> 카테고리 Map 구성 (N+1 방지)
        Map<String, Category> myCategoryMap = categoryRepository
                .findAllByUserIdAndIsDefaultTrue(userId)
                .stream()
                .collect(Collectors.toMap(Category::getName, c -> c, (a, b) -> a));

        return pendingInvitations.stream()
                .map(pm -> PromiseInvitationResponse.from(
                        pm,
                        myCategoryMap.get(pm.getPromise().getCategory().getName()),
                        findRepresentativeConflict(userId, pm.getPromise())))
                .toList();
    }

    /**
     * 초대받은 사람(userId)이 약속 시간과 겹치는 일정을 갖고 있는지 조회해 대표 충돌 일정을 반환한다.
     * (충돌 없으면 null)
     *
     * 대표 선정 기준은 초대 메시지(buildInvitationMessage)와 동일한 판단을 프론트가 재현할 수 있도록,
     * 중요도 높은(낮은 중요도 카테고리가 아닌) 충돌이 하나라도 있으면 그 일정을 우선 반환한다.
     * 전부 낮은 중요도라면 첫 번째 충돌 일정을 반환한다 ("'OO' 일정이 있는데 조정 가능할까요?" 문구의 기준).
     */
    private BusyScheduleTimeRange findRepresentativeConflict(Long userId, Promise promise) {
        List<BusyScheduleTimeRange> conflicts = promiseRecommendationService.findExpandedSchedulesInWindow(
                List.of(userId),
                promise.getStartTime().toLocalDate(),
                promise.getStartTime(),
                promise.getEndTime()
        );

        if (conflicts.isEmpty()) {
            return null;
        }

        return conflicts.stream()
                .filter(conflict -> !LOW_IMPORTANCE_CATEGORY_NAMES.contains(conflict.categoryName()))
                .findFirst()
                .orElse(conflicts.get(0));
    }


    /**
     * 약속 삭제
     *
     * 약속 생성자만 삭제 가능
     * 실제 DB row를 삭제하지 않고 isDeleted = true, status = CANCELED로 변경
     */
    @Transactional
    public void deletePromise(Long userId, Long promiseId) {

        Promise promise = promiseRepository.findByIdAndIsDeletedFalse(promiseId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_NOT_FOUND));

        validatePromiseSender(promise, userId);

        // 3. 이미 확정된 약속은 일단 삭제를 막음
        // 이유: 확정된 약속은 각 멤버의 Schedule에 이미 등록되었을 수 있는데,
        // 현재 Schedule과 Promise를 연결하는 promise_id가 없다면 자동 삭제가 어렵다.
        if (promise.getStatus() == PromiseStatus.CONFIRMED) {
            throw new CustomException(ErrorCode.PROMISE_ALREADY_CLOSED);
        }

        promise.delete();
    }


    /**
     * 약속 미응답 멤버 1명 콕찌르기
     *
     * 조건:
     * 1. 약속 생성자만 콕찌르기 가능
     * 2. 약속이 PENDING 상태일 때만 가능
     * 3. 대상 유저는 해당 약속의 멤버여야 함
     * 4. 대상 유저의 응답 상태가 PENDING이어야 함
     * 5. 같은 약속의 같은 멤버에게 하루 1회만 가능
     */
    @Transactional
    public PromisePokeResponse pokePromiseMember(
            Long userId,
            Long promiseId,
            PromisePokeRequest request
    ) {
        Promise promise = promiseRepository.findWithMembersById(promiseId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_NOT_FOUND));

        // 약속 생성자만 콕찌르기 가능
        // 약속 생성자 뿐만 아니라 다른 사람도 콕찌르기 할 수 있음
        validatePromiseMember(promise, userId);

        // 확정/취소된 약속에는 콕찌르기 불가
        validatePromisePending(promise);

        Long targetUserId = request.targetUserId();

        // 자기 자신에게 콕찌르기 방지
        if (targetUserId.equals(userId)) {
            throw new CustomException(ErrorCode.CANNOT_POKE_SELF);
        }

        PromiseMember targetMember = promise.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(targetUserId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_MEMBER_NOT_FOUND));

        // 콕찌르기를 보낸 실제 유저 (약속 생성자가 아닐 수도 있음)
        User poker = promise.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_FORBIDDEN))
                .getUser();

        // 미응답 멤버에게만 콕찌르기 가능
        if (targetMember.getStatus() != PromiseMemberStatus.PENDING) {
            throw new CustomException(ErrorCode.PROMISE_MEMBER_NOT_PENDING);
        }

        LocalDateTime now = LocalDateTime.now();

        // 같은 약속의 같은 멤버에게 하루 1회만 가능
        if (isAlreadyPokedToday(targetMember, now.toLocalDate())) {
            throw new CustomException(ErrorCode.PROMISE_POKE_ALREADY_SENT);
        }

        // 마지막 콕찌르기 시간 갱신
        targetMember.updateLastPokedAt(now);

        // 실제 알림 발송은 알림 서비스에서 처리 (누가 찔렀는지 구조화된 필드로도 함께 남김)
        notificationService.send(
                targetMember.getUser(),
                NotificationType.POKE,
                promise.getTitle(),
                poker.getNickname() + "님이 '" + promise.getTitle() + "' 약속에 응답해달라고 콕 찔렀어요!",
                "/promises/" + promiseId,
                promise.getCategory().getId(),
                promise.getId(),
                promise.getTitle(),
                promise.getRoom().getId(),
                promise.getRoom().getName(),
                promise.getStartTime(),
                poker.getId(),
                poker.getNickname(),
                poker.getProfileImageUrl()
        );

        return new PromisePokeResponse(
                targetUserId,
                now
        );
    }


    /**
     * 약속 시간이 변경되었을 때 멤버 응답 상태 초기화
     *
     * 생성자는 자동 참석 상태를 유지하고,
     * 나머지 멤버들은 변경된 시간에 대해 다시 응답해야 하므로 PENDING으로 변경한다.
     */
    private void resetMemberStatusesForTimeChange(Promise promise) {
        Long senderId = promise.getSender().getId();

        for (PromiseMember member : promise.getMembers()) {
            if (member.getUser().getId().equals(senderId)) {
                member.updateStatus(PromiseMemberStatus.ACCEPTED);
            } else {
                member.updateStatus(PromiseMemberStatus.PENDING);
            }
        }
    }



    /**
     * 추천 시간 선택 기반 약속 시간 수정

     * 추천 기반 수정:
     * - 빈 시간 추천 알고리즘을 다시 실행
     * - 사용자가 선택한 recommendedStartTime/recommendedEndTime이 실제 추천 결과에 있는지 검증
     * - selectedStartTime/selectedEndTime이 있으면 추천 범위 안에서 줄여서 수정 가능
     *
     * 중요한 정책:
     * - 기존 PromiseMember는 유지한다.
     * - 추천 결과의 availableMembers만 남기고 멤버를 삭제하지 않는다.
     * - 시간이 바뀌면 생성자는 ACCEPTED, 나머지는 PENDING으로 초기화한다.
     */
    @Transactional
    public Long updatePromiseTimeFromRecommendation(
            Long userId,
            Long promiseId,
            PromiseUpdateTimeFromRecommendationRequest request
    ) {
        // 1. 약속 조회
        // 추천 기반 수정에서는 기존 멤버 목록이 필요하므로 members까지 fetch하는 메서드 사용
        Promise promise = promiseRepository.findWithMembersById(promiseId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROMISE_NOT_FOUND));

        // 2. 약속 생성자만 시간 수정 가능
        validatePromiseSender(promise, userId);

        // 3. PENDING 상태의 약속만 수정 가능
        validatePromisePending(promise);

        // 4. 현재 약속 멤버 중 생성자를 제외한 멤버 ID 추출
        // recommendPromiseTimes()가 로그인 유저를 자동 포함하므로 생성자는 제외
        List<Long> selectedMemberIds = getPromiseMemberIdsExceptSender(promise);

        // 5. 기존 빈 시간 추천 Request로 변환
        // 즉, "약속 수정용 추천 API"를 호출했을 때와 동일한 조건으로 추천 알고리즘을 다시 실행한다.
        PromiseTimeRecommendRequest recommendRequest = new PromiseTimeRecommendRequest(
                request.proposeStartDate(),
                request.proposeEndDate(),
                request.searchStartTime(),
                request.searchEndTime(),
                request.minDuration(),
                request.sortType(),
                selectedMemberIds
        );

        // 6. 추천 알고리즘 재실행
        // 이유:
        // - 프론트가 추천 시간을 조작해서 보내는 것을 막기 위함
        // - 추천 조회 이후 누군가 일정을 추가/수정했을 수 있기 때문
        List<PromiseTimeRecommendationResponse> recommendations =
                promiseRecommendationService.recommendPromiseTimes(
                        userId,
                        promise.getRoom().getId(),
                        recommendRequest
                );

        // 7. 요청으로 받은 원본 추천 시간이 실제 추천 결과에 존재하는지 검증
        PromiseTimeRecommendationResponse selectedRecommendation = recommendations.stream()
                .filter(recommendation ->
                        recommendation.startTime().equals(request.recommendedStartTime())
                                && recommendation.endTime().equals(request.recommendedEndTime())
                )
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PROMISE_TIME));

        // 8. 최종 약속 시간 결정
        SelectedPromiseTime finalSelectedTime = resolveSelectedPromiseTime(request);

        // 9. 최종 시간이 원본 추천 범위 안에 있는지 검증
        validateAdjustedRecommendationTime(
                selectedRecommendation,
                finalSelectedTime.startTime(),
                finalSelectedTime.endTime(),
                request.minDuration()
        );

        // 10. 일반 약속 시간 검증
        validatePromiseTime(
                request.proposeStartDate(),
                request.proposeEndDate(),
                finalSelectedTime.startTime(),
                finalSelectedTime.endTime(),
                request.minDuration()
        );

        // 11. 실제로 시간이 바뀌었는지 확인
        // 시간이 바뀐 경우에만 멤버 응답 상태를 초기화한다.
        boolean isTimeChanged =
                !Objects.equals(promise.getStartTime(), finalSelectedTime.startTime())
                        || !Objects.equals(promise.getEndTime(), finalSelectedTime.endTime());

        // 12. 약속 정보 업데이트
        // 추천 기반 시간 수정은 "시간 수정"만 담당한다.
        // 따라서 title/comment/category/location/notificationLeadMinutes는 기존 값을 유지한다.
        promise.update(
                promise.getCategory(),
                promise.getTitle(),
                promise.getComment(),
                request.proposeStartDate(),
                request.proposeEndDate(),
                finalSelectedTime.startTime(),
                finalSelectedTime.endTime(),
                promise.getLocation(),
                promise.getNotificationLeadMinutes(),
                request.minDuration()
        );

        // 13. 시간이 바뀌었다면 기존 응답 상태 초기화
        // 생성자: ACCEPTED 유지
        // 나머지 멤버: PENDING
        if (isTimeChanged) {
            resetMemberStatusesForTimeChange(promise);
        }

        return promise.getId();
    }

    /**
     * 현재 약속 멤버 중 생성자를 제외한 userId 목록을 반환한다.
     *
     * recommendPromiseTimes()는 로그인 유저를 자동 포함하므로
     * 여기서는 생성자를 제외한 나머지 멤버만 반환한다.
     */
    private List<Long> getPromiseMemberIdsExceptSender(Promise promise) {
        Long senderId = promise.getSender().getId();

        List<Long> memberIds = promise.getMembers().stream()
                .map(member -> member.getUser().getId())
                .filter(memberId -> !memberId.equals(senderId))
                .distinct()
                .toList();

        if (memberIds.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_MEMBER);
        }

        return memberIds;
    }


    //===================검증 메서드======================//
    private void validatePromiseSender(Promise promise, Long userId) {
        if (!promise.getSender().getId().equals(userId)) {
            throw new CustomException(ErrorCode.PROMISE_FORBIDDEN);
        }
    }

    private void validatePromiseMember(Promise promise, Long userId) {
        boolean isPromiseMember = promise.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(userId));

        if (!isPromiseMember) {
            throw new CustomException(ErrorCode.PROMISE_FORBIDDEN);
        }
    }

    private void validatePromisePending(Promise promise) {
        if (promise.getStatus() != PromiseStatus.PENDING) {
            throw new CustomException(ErrorCode.PROMISE_ALREADY_CLOSED);
        }
    }

    private void validatePromiseTime(
            LocalDate proposeStartDate,
            LocalDate proposeEndDate,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime,
            Integer minDuration
    ) {
        if (proposeEndDate.isBefore(proposeStartDate)) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        if (!endTime.isAfter(startTime)) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        LocalDate startDate = startTime.toLocalDate();
        LocalDate endDate = endTime.toLocalDate();

        if (startDate.isBefore(proposeStartDate) || endDate.isAfter(proposeEndDate)) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        long durationMinutes = Duration.between(startTime, endTime).toMinutes();

        if (durationMinutes < minDuration) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_DURATION);
        }
    }

    private boolean isAlreadyPokedToday(PromiseMember member, LocalDate today) {
        return member.getLastPokedAt() != null
                && member.getLastPokedAt().toLocalDate().isEqual(today);
    }


    private SelectedPromiseTime resolveSelectedPromiseTime(
            PromiseCreateFromRecommendationRequest request
    ) {
        boolean selectedStartMissing = request.selectedStartTime() == null;
        boolean selectedEndMissing = request.selectedEndTime() == null;

        // 시간 수정 안 한 경우
        // selectedStartTime, selectedEndTime 둘 다 안 왔다면
        // 원본 추천 시간 그대로 약속을 생성한다.
        if (selectedStartMissing && selectedEndMissing) {
            return new SelectedPromiseTime(
                    request.recommendedStartTime(),
                    request.recommendedEndTime()
            );
        }

        // 하나만 null인 경우는 프론트 요청이 잘못된 것
        if (selectedStartMissing || selectedEndMissing) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        // 시간 수정한 경우
        return new SelectedPromiseTime(
                request.selectedStartTime(),
                request.selectedEndTime()
        );
    }

    private SelectedPromiseTime resolveSelectedPromiseTime(
            PromiseUpdateTimeFromRecommendationRequest request
    ) {
        boolean selectedStartMissing = request.selectedStartTime() == null;
        boolean selectedEndMissing = request.selectedEndTime() == null;

        if (selectedStartMissing && selectedEndMissing) {
            return new SelectedPromiseTime(
                    request.recommendedStartTime(),
                    request.recommendedEndTime()
            );
        }

        if (selectedStartMissing || selectedEndMissing) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        return new SelectedPromiseTime(
                request.selectedStartTime(),
                request.selectedEndTime()
        );
    }

    private static final int SLOT_MINUTES = 30;

    private void validateAdjustedRecommendationTime(
            PromiseTimeRecommendationResponse recommendation,
            LocalDateTime selectedStartTime,
            LocalDateTime selectedEndTime,
            Integer minDuration
    ) {
        // 최종 시작 시간이 원래 추천 시작보다 빠르면 안 됨
        if (selectedStartTime.isBefore(recommendation.startTime())) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        // 최종 종료 시간이 원래 추천 종료보다 늦으면 안 됨
        if (selectedEndTime.isAfter(recommendation.endTime())) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        // 종료 시간이 시작 시간보다 뒤여야 함
        if (!selectedEndTime.isAfter(selectedStartTime)) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }

        long durationMinutes = Duration.between(selectedStartTime, selectedEndTime).toMinutes();

        // 최종 선택 시간이 minDuration 이상이어야 함
        if (durationMinutes < minDuration) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_DURATION);
        }

        // 추천 알고리즘이 30분 단위라면 최종 선택 시간도 30분 단위로 제한하는 것을 추천
        if (!isAlignedToSlot(selectedStartTime) || !isAlignedToSlot(selectedEndTime)) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_TIME);
        }
    }

    private boolean isAlignedToSlot(LocalDateTime time) {
        return time.getSecond() == 0
                && time.getNano() == 0
                && time.getMinute() % SLOT_MINUTES == 0;
    }

    private record SelectedPromiseTime(
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
    }
}


