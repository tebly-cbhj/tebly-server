package com.example.teblyserver.promise.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.promise.domain.Promise;
import com.example.teblyserver.promise.domain.PromiseMember;
import com.example.teblyserver.promise.domain.PromiseMemberStatus;
import com.example.teblyserver.promise.domain.PromiseStatus;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PromiseService {

    private final PromiseRepository promiseRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PromiseMemberRepository promiseMemberRepository;
    private final ScheduleService scheduleService;
    private final PromiseRecommendationService promiseRecommendationService;
    //private final NotificationService notificationService;

    /**
     * 새로운 약속 생성
     */
    @Transactional
    public Long createPromise(Long userId, Long roomId, PromiseCreateRequest request) {

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

        return savedPromise.getId();
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
                        recommendation.startTime().equals(request.selectedStartTime())
                                && recommendation.endTime().equals(request.selectedEndTime())
                )
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PROMISE_TIME));

        // 4. 선택한 추천 시간에서 가능한 멤버만 추출한다.
        // availableMembers에는 생성자 본인도 포함되어 있을 수 있다.
        List<Long> availableMemberIds = selectedRecommendation.availableMembers().stream()
                .map(member -> member.userId())
                .distinct()
                .toList();

        // 5. 생성자가 이 시간에 가능하지 않다면 약속을 만들 수 없다.
        if (!availableMemberIds.contains(userId)) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_MEMBER);
        }

        // 6. PromiseCreateRequest의 inviteeIds에는 생성자를 제외한 가능한 멤버만 넣는다.
        // 생성자는 createPromise() 안에서 자동으로 PromiseMember에 추가되고 ACCEPTED 처리된다.
        List<Long> inviteeIds = availableMemberIds.stream()
                .filter(availableMemberId -> !availableMemberId.equals(userId))
                .toList();

        // 7. 가능한 상대방이 아무도 없으면 약속 생성 불가로 처리한다.
        if (inviteeIds.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PROMISE_MEMBER);
        }

        // 8. 기존 약속 생성 DTO로 변환한다.
        PromiseCreateRequest createRequest = new PromiseCreateRequest(
                request.title(),
                request.comment(),
                request.categoryId(),
                request.proposeStartDate(),
                request.proposeEndDate(),
                selectedRecommendation.startTime(),
                selectedRecommendation.endTime(),
                request.location(),
                request.notificationLeadMinutes(),
                request.minDuration(),
                inviteeIds
        );

        // 9. 기존 createPromise() 로직 재사용
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

        return pendingInvitations.stream()
                .map(PromiseInvitationResponse::from)
                .toList();
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

        // 실제 알림 발송은 알림 서비스에서 처리
        // TODO : 여기에 알림 서비스 주입 받아서 알림 처리 해야될듯

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



}
