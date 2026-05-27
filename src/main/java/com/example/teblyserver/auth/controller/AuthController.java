package com.example.teblyserver.auth.controller;

import com.example.teblyserver.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/token/refresh")
    public ResponseEntity<?> reissue(@RequestBody java.util.Map<String, String> request) {
        String refreshToken = request.get("refresh_token");
        return ResponseEntity.ok(authService.reissue(refreshToken));
    }

    @PostMapping("/signout")
    public ResponseEntity<?> signout(@RequestBody java.util.Map<String, String> request) {
        String refreshToken = request.get("refresh_token");
        authService.signout(refreshToken);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/withdraw")
    public ResponseEntity<?> withdraw(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.withdraw(userId);
        return ResponseEntity.ok().build();
    }
}