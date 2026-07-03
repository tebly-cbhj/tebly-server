package com.example.teblyserver.decision.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.decision.dto.response.LLMDecisionResponseDto;
import com.example.teblyserver.decision.service.DecisionHelperService;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class DecisionHelperController {

    private final DecisionHelperService decisionHelperService;

    /**
     * 결정이 어려울 때 도우미 API
     * URL: POST /api/v1/rooms/{roomId}/decision-helper
     *
     * 캐시 없이 매 호출마다 추천 알고리즘(PromiseRecommendationService)을 직접 재실행한다.
     * request body는 추천 API(PromiseTimeRecommendRequest)와 동일한 형식이다.
     */
    @PostMapping("/{roomId}/decision-helper")
    public ResponseEntity<ApiResponse<LLMDecisionResponseDto>> decide(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @Valid @RequestBody PromiseTimeRecommendRequest request
    ) {
        LLMDecisionResponseDto response = decisionHelperService.decide(userId, roomId, request);

        return ResponseEntity.ok(ApiResponse.success("결정 도우미 응답 생성 성공", response));
    }
}
