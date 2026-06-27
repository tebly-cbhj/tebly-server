package com.example.teblyserver.promise.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendRequest;
import com.example.teblyserver.promise.dto.response.PromiseTimeRecommendationResponse;
import com.example.teblyserver.promise.service.PromiseRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PromiseRecommendationController {

    private final PromiseRecommendationService promiseRecommendationService;

    /**
     * 빈 시간 추천 API
     *
     * URL: POST /rooms/{roomId}/promise-time-recommendations
     */
    @PostMapping("/rooms/{roomId}/promise-time-recommendations")
    public ResponseEntity<ApiResponse<List<PromiseTimeRecommendationResponse>>> recommendPromiseTimes(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @Valid @RequestBody PromiseTimeRecommendRequest request
    ) {
        List<PromiseTimeRecommendationResponse> response =
                promiseRecommendationService.recommendPromiseTimes(userId, roomId, request);

        return ResponseEntity.ok(
                ApiResponse.success("빈 시간 추천에 성공했습니다.", response)
        );
    }
}
