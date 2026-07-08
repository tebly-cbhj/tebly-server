package com.example.teblyserver.decision.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.decision.dto.request.DecisionHelperPromiseRequest;
import com.example.teblyserver.decision.dto.response.LLMDecisionResponseDto;
import com.example.teblyserver.decision.service.DecisionHelperService;
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
     * 후보가 있는 경우(type=recommendation)에는 LLM이 고른 시간으로 실제 약속을 생성하고,
     * 초대 메시지는 멤버 상황별로 다르게 보내며, 방 채팅에 선택 사유를 공유한다.
     * 후보가 없는 경우(type=alternative)는 검증되지 않은 제안이라 약속을 생성하지 않는다.
     */
    @PostMapping("/{roomId}/decision-helper")
    public ResponseEntity<ApiResponse<LLMDecisionResponseDto>> decide(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @Valid @RequestBody DecisionHelperPromiseRequest request
    ) {
        LLMDecisionResponseDto response = decisionHelperService.decide(userId, roomId, request);

        return ResponseEntity.ok(ApiResponse.success("결정 도우미 응답 생성 성공", response));
    }
}
