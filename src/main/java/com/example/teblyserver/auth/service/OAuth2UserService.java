package com.example.teblyserver.auth.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        System.out.println("=== 카카오 attributes ===");
        System.out.println(oAuth2User.getAttributes());

        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String oauthId;
        String nickname;
        String profileImageUrl;
        String email;

        if (provider.equals("kakao")) {
            oauthId = String.valueOf(attributes.get("id"));
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            nickname = (String) profile.get("nickname");
            profileImageUrl = (String) profile.get("profile_image_url");
            email = kakaoAccount.containsKey("email") ? (String) kakaoAccount.get("email") : "";
        } else { // google
            oauthId = (String) attributes.get("sub");
            nickname = (String) attributes.get("name");
            profileImageUrl = (String) attributes.get("picture");
            email = (String) attributes.get("email");
        }

        User user = userRepository.findByOauthId(oauthId)
                .orElseGet(() -> userRepository.save(
                        User.create(email, provider, oauthId, nickname, profileImageUrl)
                ));

        return oAuth2User;
    }
}