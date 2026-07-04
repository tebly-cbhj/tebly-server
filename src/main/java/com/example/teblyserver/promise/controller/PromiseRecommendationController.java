package com.example.teblyserver.promise.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.promise.dto.request.PromiseTimeRecommendRequest;
import com.example.teblyserver.promise.dto.request.PromiseUpdateTimeRecommendRequest;
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

    /**
     * 약속 수정용 빈 시간 추천 API
     *
     * URL: POST /promises/{promiseId}/time-recommendations
     *
     * 역할:
     * - 기존 약속의 멤버들을 기준으로 빈 시간 추천을 다시 실행한다.
     * - 프론트가 selectedMemberIds를 보내지 않는다.
     * - 백엔드가 현재 PromiseMember 목록을 보고 추천 대상 멤버를 구성한다.
     *
     * 사용 흐름:
     * 약속 상세/수정 화면
     * → "추천으로 시간 수정" 클릭
     * → 이 API 호출
     * → 추천 시간 리스트 반환
     */
    @PostMapping("/promises/{promiseId}/time-recommendations")
    public ResponseEntity<ApiResponse<List<PromiseTimeRecommendationResponse>>> recommendPromiseUpdateTimes(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long promiseId,
            @Valid @RequestBody PromiseUpdateTimeRecommendRequest request
    ) {
        List<PromiseTimeRecommendationResponse> response =
                promiseRecommendationService.recommendPromiseUpdateTimes(
                        userId,
                        promiseId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success("약속 수정용 빈 시간 추천에 성공했습니다.", response)
        );
    }
}
