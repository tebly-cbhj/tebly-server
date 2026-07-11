package com.example.teblyserver.friend.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.friend.domain.Friendship;
import com.example.teblyserver.friend.dto.FriendResponse;
import com.example.teblyserver.friend.repository.FriendshipRepository;
import com.example.teblyserver.schedule.dto.response.ScheduleResponseDto;
import com.example.teblyserver.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final ScheduleService scheduleService;

    // 친구 목록 조회
    public List<FriendResponse> getFriends(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<FriendResponse> friends = friendshipRepository.findByRequester(user)
                .stream().map(f -> new FriendResponse(f.getReceiver())).toList();

        List<FriendResponse> received = friendshipRepository.findByReceiver(user)
                .stream().map(f -> new FriendResponse(f.getRequester())).toList();

        List<FriendResponse> result = new java.util.ArrayList<>(friends);
        result.addAll(received);
        return result;
    }

    // 초대 코드로 상대방 미리보기 (친구 추가 전, 누구인지 확인용)
    public FriendResponse previewByCode(String inviteCode) {
        User user = userRepository.findByInvitationCode(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return new FriendResponse(user);
    }

    // 초대 코드로 친구 추가
    @Transactional
    public void addFriendByCode(Long userId, String inviteCode) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User receiver = userRepository.findByInvitationCode(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (friendshipRepository.existsByRequesterAndReceiver(requester, receiver)) {
            throw new CustomException(ErrorCode.DUPLICATE_FRIENDSHIP);
        }

        friendshipRepository.save(Friendship.create(requester, receiver));
    }

    // 링크로 친구 추가
    @Transactional
    public void addFriendByLink(Long userId, String inviteToken) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User receiver = userRepository.findByInvitationCode(inviteToken)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (friendshipRepository.existsByRequesterAndReceiver(requester, receiver)) {
            throw new CustomException(ErrorCode.DUPLICATE_FRIENDSHIP);
        }

        friendshipRepository.save(Friendship.create(requester, receiver));
    }

    // 친구 삭제
    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Friendship friendship = friendshipRepository.findByRequesterAndReceiver(user, friend)
                .orElseGet(() -> friendshipRepository.findByRequesterAndReceiver(friend, user)
                        .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND)));

        friendshipRepository.delete(friendship);
    }

    // 친구 일정 조회
    public ScheduleResponseDto getFriendSchedule(Long userId, Long friendId, String view, LocalDate targetDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 친구 관계인지 확인
        boolean isFriend = friendshipRepository.existsByRequesterAndReceiver(user, friend)
                || friendshipRepository.existsByRequesterAndReceiver(friend, user);

        if (!isFriend) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return scheduleService.getFriendSchedules(userId, friendId, view, targetDate);
    }
}