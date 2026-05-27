package com.example.teblyserver.auth.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.dto.UserProfileRequest;
import com.example.teblyserver.auth.dto.UserProfileResponse;
import com.example.teblyserver.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public void updateProfile(Long userId, UserProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));

        user.updateProfile(request.getNickname(), request.getProfileImageUrl());
    }

    public UserProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));
        return new UserProfileResponse(user);
    }
}