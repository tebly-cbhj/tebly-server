package com.example.teblyserver.promise.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.promise.dto.request.PromiseCreateRequest;
import com.example.teblyserver.promise.dto.response.PromiseDetailResponse;
import com.example.teblyserver.promise.service.PromiseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/promises")
@RequiredArgsConstructor
public class PromiseController {

    private final PromiseService promiseService;

    /**
     * 약속 생성 API
     * URL: POST /promises
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createPromise(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PromiseCreateRequest request
    ) {
        Long promiseId = promiseService.createPromise(userId, request);

        return ResponseEntity.ok(ApiResponse.success("약속이 성공적으로 생성되었습니다.", promiseId));
    }

    /**
     * 약속 상세 조회 API
     *
     * 요청:
     * GET /promises/{promiseId}
     * 역할:
     * - 약속 기본 정보 조회
     * - 약속이 속한 방 정보 조회
     * - 약속 생성자 정보 조회
     * - 약속 카테고리 정보 조회
     * - 약속 멤버별 응답 상태 조회
     * - 수락/거절/미응답 인원 수 조회
     *
     * @param userId 현재 로그인한 사용자 ID
     * @param promiseId 조회할 약속 ID
     * @return 약속 상세 정보
     */
    @GetMapping("/{promiseId}")
    public ResponseEntity<ApiResponse<PromiseDetailResponse>> getPromiseDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long promiseId
    ) {
        PromiseDetailResponse response = promiseService.getPromiseDetail(userId, promiseId);

        return ResponseEntity.ok(ApiResponse.success("약속 상세 조회에 성공했습니다.", response));
    }
}
