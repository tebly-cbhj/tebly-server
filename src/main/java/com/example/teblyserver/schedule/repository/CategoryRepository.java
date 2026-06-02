package com.example.teblyserver.schedule.repository;

import com.example.teblyserver.schedule.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    /**
     * 특정 유저의 카테고리 목록 전체 조회
     * - 로그인한 유저가 자신의 카테고리 탭을 열었을 때,
     * 서버가 가입 시 넣어준 '디폴트 9개' + 유저가 직접 만든 '커스텀 N개'를 모두 불러옵니다.
     */
    List<Category> findAllByUserId(Long userId);

    /**
     * 유저별 카테고리명 중복 검사
     * - 유저가 새 커스텀 카테고리를 만들 때, 이미 있는 이름("전공수업" 등)으로
     * 또 만들려고 하면 막아주기 위한 검증용 메서드
     */
    boolean existsByUserIdAndName(Long userId, String name);

    Optional<Category> findByUserIdAndNameAndIsDefaultTrue(Long userId, String name);
}
