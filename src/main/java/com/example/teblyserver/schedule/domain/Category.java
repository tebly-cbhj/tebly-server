package com.example.teblyserver.schedule.domain;

import com.example.teblyserver.auth.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String icon; // 카테고리 아이콘 URL 또는 식별자

    // 카테고리별 비공개 여부 (기본값: 공개)
    @Column(nullable = false)
    private boolean isPrivate = false;

    // 시스템이 부여한 기본 카테고리인지 식별하는 플래그
    @Column(nullable = false)
    private boolean isDefault = false;

    /* =================================================================
       정적 팩토리 메서드 (객체 생성용)
    ================================================================= */

    // 1. 유저가 직접 만드는 [커스텀 카테고리] 생성 메서드
    public static Category createCustom(User user, String name, String icon, boolean isPrivate) {
        Category category = new Category();
        category.user = user;
        category.name = name;
        category.icon = icon;
        category.isPrivate = isPrivate;
        category.isDefault = false; // 유저가 만든 건 무조건 false
        return category;
    }

    // 2. 유저가 회원가입할 때 서버가 자동으로 넣어줄 [디폴트 카테고리 9개] 생성용
    public static Category createDefault(User user, String name, String icon) {
        Category category = new Category();
        category.user = user;
        category.name = name;
        category.icon = icon;
        category.isPrivate = false;
        category.isDefault = true;  // 디폴트 카테고리 표시
        return category;
    }

    /* =================================================================
       비즈니스 로직 (수정용)
    ================================================================= */

    // 커스텀 카테고리용 전체 수정 (이름, 아이콘, 비공개 여부 다 됨)
    public void updateAll(String name, String icon, Boolean isPrivate) {
        if (name != null && !name.isBlank()) this.name = name;
        if (icon != null && !icon.isBlank()) this.icon = icon;
        if (isPrivate != null) this.isPrivate = isPrivate;
    }

    // 디폴트 카테고리용 전용 수정 (오직 비공개 여부만 토글 가능)
    public void updatePrivacyOnly(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
}
