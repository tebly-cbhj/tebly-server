package com.example.teblyserver.auth.service;

import com.example.teblyserver.auth.domain.RefreshToken;
import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.RefreshTokenRepository;
import com.example.teblyserver.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 구글은 sub, 카카오는 id
        String oauthId = attributes.containsKey("sub")
                ? (String) attributes.get("sub")
                : String.valueOf(attributes.get("id"));

        User user = userRepository.findByOauthId(oauthId).orElseThrow();

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        // refresh token DB에 저장
        refreshTokenRepository.findById(user.getId())
                .ifPresentOrElse(
                        rt -> rt.updateToken(refreshToken),
                        () -> refreshTokenRepository.save(RefreshToken.create(user.getId(), refreshToken))
                );

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"access_token\":\"" + accessToken + "\"," +
                        "\"refresh_token\":\"" + refreshToken + "\"," +
                        "\"is_new_user\":" + user.isNewUser() + "}"
        );
    }
}