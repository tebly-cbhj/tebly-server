package com.example.teblyserver.auth.service;

import com.example.teblyserver.auth.domain.RefreshToken;
import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.RefreshTokenRepository;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    // 토큰 재발급
    @Transactional
    public Map<String, String> reissue(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        Long userId = jwtService.getUserId(refreshToken);

        String newAccessToken = jwtService.generateAccessToken(userId);
        String newRefreshToken = jwtService.generateRefreshToken(userId);

        stored.updateToken(newRefreshToken);

        return Map.of(
                "access_token", newAccessToken,
                "refresh_token", newRefreshToken
        );
    }

    // 로그아웃
    @Transactional
    public void signout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    // 회원 탈퇴
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        // soft delete
        user.softDelete();
        refreshTokenRepository.deleteById(userId);
    }
}