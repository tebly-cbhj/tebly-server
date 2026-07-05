package com.example.teblyserver.schedule.dto.response;

import com.example.teblyserver.schedule.domain.Category;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoryResponseDto(
        Long categoryId,
         String categoryName,
         String categoryIcon,
        // 프론트엔드가 "비공개인 카테고리"를 표시해주기 위해 추가
         boolean isPrivate
) {
    // 마스킹 여부(isMasked)를 파라미터로 받아서 알아서 변환해 주는 팩토리 메서드
    public static CategoryResponseDto of(Category category, boolean isMasked) {
        // 1. [친구 관계] 친구가 볼 때는 마스킹 처리를 해서 보냄
        if (isMasked) {
            return new CategoryResponseDto(
                    category.getId(),
                    "일정",              // 마스킹 처리
                    // TODO: 기타 아이콘 URL 나오면 수정
                    "default_icon_url", // 마스킹 아이콘
                    true
            );
        }

        // 2. [본인 참조] 내가 내 시간표를 조회할 때
        return new CategoryResponseDto(
                category.getId(),
                category.getName(),
                category.getIcon(),
                category.isPrivate()
        );
    }
}
