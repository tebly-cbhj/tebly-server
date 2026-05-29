package com.example.teblyserver.schedule.service;

import com.example.teblyserver.auth.domain.User;
import com.example.teblyserver.auth.repository.UserRepository;
import com.example.teblyserver.common.exception.CustomException;
import com.example.teblyserver.common.exception.ErrorCode;
import com.example.teblyserver.schedule.domain.Category;
import com.example.teblyserver.schedule.dto.request.CategoryCreateRequestDto;
import com.example.teblyserver.schedule.dto.request.CategoryUpdateRequestDto;
import com.example.teblyserver.schedule.dto.response.CategoryResponseDto;
import com.example.teblyserver.schedule.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository; // 유저 검증을 위한 주입

    /**
     * 유저의 전체 카테고리 목록 조회
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> getCategories(Long userId) {

        // 1. 유저 존재 여부 검증 (방어 로직)
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. DB에서 해당 유저의 모든 카테고리 조회 (디폴트 9개 + 커스텀 N개)
        List<Category> categories = categoryRepository.findAllByUserId(userId);

        // 3. Entity 리스트를 DTO 리스트로 변환
        return categories.stream()
                // 본인이 본인 것을 조회하는 것이므로 마스킹(isMasked) 파라미터는 무조건 false!
                .map(category -> CategoryResponseDto.of(category, false))
                .collect(Collectors.toList());
    }

    /**
     * 유저 커스텀 카테고리 추가
     */
    @Transactional
    public Long createCategory(Long userId, CategoryCreateRequestDto requestDto) {

        // 1. 유저 조회 (DB에서 실제 User 엔티티를 긁어옴)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 2. 카테고리 이름 중복 검사
        // 만약 이 유저가 이미 똑같은 이름의 카테고리를 가지고 있다면 예외 발생!
        if (categoryRepository.existsByUserIdAndName(userId, requestDto.name())) {
            throw new CustomException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }

        // 3. 엔티티 생성
        Category category = Category.createCustom(
                user,
                requestDto.name(),
                requestDto.icon(),
                requestDto.isPrivate()
        );

        // 4. DB에 최종 저장
        Category savedCategory = categoryRepository.save(category);

        return savedCategory.getId();
    }

    /**
     * 카테고리 정보 수정
     */
    @Transactional
    public Long updateCategory(Long userId, Long categoryId, CategoryUpdateRequestDto requestDto) {

        // 1. 카테고리 엔티티 조회
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        // 2. [보안 검증] 내 카테고리가 맞는지 확인 (남의 카테고리 수정 방지)
        if (!category.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.CATEGORY_FORBIDDEN);
        }

        // 3. [비즈니스 로직 분기] 디폴트 카테고리 vs 커스텀 카테고리
        if (category.isDefault()) {
            // 디폴트 카테고리: 비공개 여부만 수정 가능 (토글)
            // 화면 상 디폴트 카테고리의 수정은 비공개 여부만 가능
            // 만약 다른 경로로 접근해서 이름을 변경한다고 하더라도 무시
            if (requestDto.isPrivate() != null) {
                category.updatePrivacyOnly(requestDto.isPrivate());
            }
        } else {
            // 커스텀 카테고리: 이름, 아이콘, 비공개 여부 모두 변경 가능

            // 이름이 변경되었다면 중복 검사 진행
            if (requestDto.name() != null && !requestDto.name().equals(category.getName())) {
                if (categoryRepository.existsByUserIdAndName(userId, requestDto.name())) {
                    throw new CustomException(ErrorCode.DUPLICATE_CATEGORY_NAME);
                }
            }

            category.updateAll(requestDto.name(), requestDto.icon(), requestDto.isPrivate());
        }

        // Dirty Checking 작동
        return category.getId();
    }

    /*
    * TODO: 삭제 로직 구현
    * 생각해봐야 할 것
    *  - 카테고리 삭제시 연관되어 있는 일정들은 어떻게 할 것인지
    *  - 카테고리의 용도? 카테고리 별로 모아서 일정 조회? 그냥 일정 조회 시에 카테고리가 들어가나? 색갈같은걸로 구분?
    *  - 카테고리를 nullable=false 로 설정하면 어떨까..
     */
}
