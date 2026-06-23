package com.example.teblyserver.promise.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.promise.domain.Promise;
import com.example.teblyserver.promise.domain.PromiseMember;
import com.example.teblyserver.promise.domain.PromiseMemberStatus;
import com.example.teblyserver.promise.dto.request.PromiseCreateRequest;
import com.example.teblyserver.promise.dto.response.PromiseDetailResponse;
import com.example.teblyserver.promise.repository.PromiseRepository;
import com.example.teblyserver.room.domain.InviteStatus;
import com.example.teblyserver.room.domain.Room;
import com.example.teblyserver.room.domain.RoomMember;
import com.example.teblyserver.room.repository.RoomRepository;
import com.example.teblyserver.schedule.domain.Category;
import com.example.teblyserver.schedule.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromiseService {

    private final PromiseRepository promiseRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    /**
     * 새로운 약속 생성
     */
    @Transactional
    public Long createPromise(Long userId, PromiseCreateRequest request) {

        // 1. 필요한 엔티티들 조회 (유저, 방)
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 권한 검증: 약속을 제안하는 유저가 이 방의 수락된 멤버가 맞는지 확인
        boolean isRoomMember = room.getMembers().stream()
                .anyMatch(rm -> rm.getUser().getId().equals(userId) && rm.getInviteStatus() == InviteStatus.ACCEPTED);
        if (!isRoomMember) {
            throw new CustomException(ErrorCode.PROMISE_FORBIDDEN);
        }

        // 3. 카테고리 조회 (선택 사항이므로 null 체크)
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        // 4. 약속(Promise) 엔티티 생성
        Promise promise = Promise.create(
                room, sender, category, request.title(),
                request.comment(), request.proposeStartDate(), request.proposeEndDate(), request.minDuration()
        );

        // 5. 방에 있는 '수락된' 멤버들을 모두 이 약속의 멤버(PENDING)로 초대
        for (RoomMember roomMember : room.getMembers()) {
            if (roomMember.getInviteStatus() == InviteStatus.ACCEPTED) {
                // PromiseMember를 생성해서 Promise의 주머니(members)에 쏙 넣어줍니다.
                PromiseMember newPromiseMember = PromiseMember.create(roomMember.getUser(), promise);

                // 약속 생성자는 본인이 만든 약속이므로 기본 참석 상태로 저장
                if (roomMember.getUser().getId().equals(userId)) {
                    newPromiseMember.updateStatus(PromiseMemberStatus.ACCEPTED);
                }

                promise.getMembers().add(newPromiseMember);
            }
        }

        // 6. DB 저장
        Promise savedPromise = promiseRepository.save(promise);

        return savedPromise.getId();
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

        // 4. 엔티티를 응답 DTO로 변환해서 반환
        return PromiseDetailResponse.from(promise, userId);
    }
}
