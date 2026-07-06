package com.example.teblyserver.auth.service;

import com.example.teblyserver.auth.domain.RefreshToken;
import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.RefreshTokenRepository;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final String FRONTEND_CALLBACK_URL = "https://tebly-client.vercel.app/login/callback";

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            Map<String, Object> attributes = oAuth2User.getAttributes();

            String oauthId = attributes.containsKey("sub")
                    ? (String) attributes.get("sub")
                    : String.valueOf(attributes.get("id"));

            User user = userRepository.findByOauthId(oauthId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            String accessToken = jwtService.generateAccessToken(user.getId());
            String refreshToken = jwtService.generateRefreshToken(user.getId());

            refreshTokenRepository.findById(user.getId())
                    .ifPresentOrElse(
                            rt -> rt.updateToken(refreshToken),
                            () -> refreshTokenRepository.save(RefreshToken.create(user.getId(), refreshToken))
                    );

            String redirectUrl = UriComponentsBuilder.fromUriString(FRONTEND_CALLBACK_URL)
                    .queryParam("access_token", accessToken)
                    .queryParam("refresh_token", refreshToken)
                    .queryParam("is_new_user", user.isNewUser())
                    .build()
                    .toUriString();

            response.sendRedirect(redirectUrl);

        } catch (CustomException e) {
            log.error("OAuth2 로그인 처리 중 오류 발생: {}", e.getErrorCode().getMessage());
            String errorRedirectUrl = UriComponentsBuilder.fromUriString(FRONTEND_CALLBACK_URL)
                    .queryParam("error", e.getErrorCode().getCode())
                    .build()
                    .toUriString();
            response.sendRedirect(errorRedirectUrl);
        }
    }
}