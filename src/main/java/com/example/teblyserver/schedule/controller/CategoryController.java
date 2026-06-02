package com.example.teblyserver.schedule.controller;

import com.example.teblyserver.common.response.ApiResponse;
import com.example.teblyserver.schedule.dto.request.CategoryCreateRequestDto;
import com.example.teblyserver.schedule.dto.request.CategoryUpdateRequestDto;
import com.example.teblyserver.schedule.dto.response.CategoryResponseDto;
import com.example.teblyserver.schedule.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/schedules/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 카테고리 목록 조회 API (Read)
     * URL: GET /schedules/categories
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponseDto>>> getCategories(
            @AuthenticationPrincipal Long userId
    ) {
        List<CategoryResponseDto> response = categoryService.getCategories(userId);
        return ResponseEntity.ok(ApiResponse.success("카테고리 목록 조회에 성공했습니다.", response));
    }

    /**
     * 카테고리 추가 API (Create)
     * URL: POST /schedules/categories
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createCategory(
            @AuthenticationPrincipal Long userId,
            @RequestBody CategoryCreateRequestDto requestDto
    ) {
        Long categoryId = categoryService.createCategory(userId, requestDto);

        // 생성 성공 시 201 Created 상태 코드와 함께 반환
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("카테고리가 성공적으로 추가되었습니다.", categoryId));
    }

    /**
     * 카테고리 이름/공개 변경 API (Update)
     * URL: PATCH /schedules/categories/{category_id}
     */
    @PatchMapping("/{category_id}")
    public ResponseEntity<ApiResponse<Long>> updateCategory(
            @AuthenticationPrincipal Long userId,
            @PathVariable("category_id") Long categoryId,
            @RequestBody CategoryUpdateRequestDto requestDto
    ) {
        Long updatedId = categoryService.updateCategory(userId, categoryId, requestDto);

        return ResponseEntity.ok(ApiResponse.success("카테고리가 성공적으로 수정되었습니다.", updatedId));
    }

    /**
     * 카테고리 삭제 API (Delete)
     * URL: DELETE /schedules/categories/{category_id}
     */
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long categoryId
    ) {
        // 1. 서비스 로직 호출 (마이그레이션 + 삭제 진행)
        categoryService.deleteCategory(categoryId, userId);

        // 2. 성공 메시지 반환
        return ResponseEntity.ok(ApiResponse.success("카테고리가 성공적으로 삭제되었습니다.", null));
    }
}
