package com.example.teblyserver.promise.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.promise.dto.request.PromiseCreateRequest;
import com.example.teblyserver.promise.dto.request.PromisePokeRequest;
import com.example.teblyserver.promise.dto.request.PromiseUpdateRequest;
import com.example.teblyserver.promise.dto.response.PromiseDetailResponse;
import com.example.teblyserver.promise.dto.request.PromiseInvitationRespondRequest;
import com.example.teblyserver.promise.dto.response.PromisePokeResponse;
import com.example.teblyserver.promise.service.PromiseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PromiseController {

    private final PromiseService promiseService;

    /**
     * 약속 생성 API
     * URL: POST /rooms/{roomId}/promises
     */
    @PostMapping("/rooms/{roomId}/promises")
    public ResponseEntity<ApiResponse<Long>> createPromise(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @Valid @RequestBody PromiseCreateRequest request
    ) {
        Long promiseId = promiseService.createPromise(userId, roomId, request);

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
    @GetMapping("/promises/{promiseId}")
    public ResponseEntity<ApiResponse<PromiseDetailResponse>> getPromiseDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long promiseId
    ) {
        PromiseDetailResponse response = promiseService.getPromiseDetail(userId, promiseId);

        return ResponseEntity.ok(ApiResponse.success("약속 상세 조회에 성공했습니다.", response));
    }


    /**
     * 약속 수정
     * URL: PATCH /promises/{promiseId}
     */
    @PatchMapping("/promises/{promiseId}")
    public ResponseEntity<ApiResponse<Long>> updatePromise(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long promiseId,
            @Valid @RequestBody PromiseUpdateRequest request
    ) {
        Long updatedPromiseId = promiseService.updatePromise(userId, promiseId, request);

        return ResponseEntity.ok(ApiResponse.success("약속이 성공적으로 수정되었습니다.", updatedPromiseId));
    }

    /**
     * 약속 확정
     * URL: PATCH /promises/{promiseId}/confirm
     */
    @PatchMapping("/promises/{promiseId}/confirm")
    public ResponseEntity<ApiResponse<Long>> confirmPromise(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long promiseId
    ) {
        Long confirmedPromiseId = promiseService.confirmPromise(userId, promiseId);

        return ResponseEntity.ok(ApiResponse.success("약속이 성공적으로 확정되었습니다.", confirmedPromiseId));
    }

    /**
     * 약속 초대장 수락/거절 API
     *
     * 약속 상세 화면에서 수락/거절할 때도 사용하고,
     * 알림 초대장 화면에서 수락/거절할 때도 같은 API를 사용한다.
     *
     * URL: PATCH /promises/{promiseId}/invitations/me
     */
    @PatchMapping("/promises/{promiseId}/invitations/me")
    public ResponseEntity<ApiResponse<Void>> respondInvitation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long promiseId,
            @Valid @RequestBody PromiseInvitationRespondRequest request
    ) {
        promiseService.respondInvitation(userId, promiseId, request);

        return ResponseEntity.ok(ApiResponse.success("약속 초대장 응답이 저장되었습니다.", null));
    }

    /**
     * 약속 삭제 API
     * URL: DELETE /promises/{promiseId}
     */
    @DeleteMapping("/promises/{promiseId}")
    public ResponseEntity<ApiResponse<Void>> deletePromise(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long promiseId
    ) {
        promiseService.deletePromise(userId, promiseId);

        return ResponseEntity.ok(ApiResponse.success("약속이 성공적으로 삭제되었습니다.", null));
    }

    /**
     * 약속 미응답 멤버 콕찌르기 API
     *
     * URL: POST /promises/{promiseId}/poke
     */
    @PostMapping("/promises/{promiseId}/poke")
    public ResponseEntity<ApiResponse<PromisePokeResponse>> pokePromiseMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long promiseId,
            @Valid @RequestBody PromisePokeRequest request
    ) {
        PromisePokeResponse response = promiseService.pokePromiseMember(
                userId,
                promiseId,
                request
        );

        return ResponseEntity.ok(ApiResponse.success("콕찌르기 알림을 보냈습니다.", response));
    }
}
