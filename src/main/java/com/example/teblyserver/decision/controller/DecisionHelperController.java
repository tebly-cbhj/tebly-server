package com.example.teblyserver.decision.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.decision.dto.response.LLMDecisionResponseDto;
import com.example.teblyserver.decision.service.DecisionHelperService;
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
     * 추천 알고리즘 결과는 Redis에 캐싱되어 있으므로 roomId만 받아 캐시에서 꺼내 LLM을 호출한다.
     */
    @PostMapping("/{roomId}/decision-helper")
    public ResponseEntity<ApiResponse<LLMDecisionResponseDto>> decide(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        LLMDecisionResponseDto response = decisionHelperService.decide(roomId);

        return ResponseEntity.ok(ApiResponse.success("결정 도우미 응답 생성 성공", response));
    }
}
