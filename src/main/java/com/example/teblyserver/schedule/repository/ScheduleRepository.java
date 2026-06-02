package com.example.teblyserver.schedule.repository;

import com.example.teblyserver.schedule.domain.Category;
import com.example.teblyserver.schedule.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // 특정 기간 사이에 포함된 일정만 조회 (N+1 방지를 위한 FETCH JOIN 추가)
    @Query("SELECT s FROM Schedule s " +
            "JOIN FETCH s.category c " +
            "JOIN FETCH s.user u " +
            "WHERE s.user.id = :userId " +
            "AND s.startTime <= :endDateTime AND s.endTime >= :startDateTime")
    List<Schedule> findSchedulesWithinRange(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // 타겟 카테고리를 가진 모든 일정을 기본 카테고리로 일괄 업데이트
    @Modifying(clearAutomatically = true) // 벌크 연산 후 영속성 컨텍스트(캐시)를 비워주는 필수 옵션
    @Query("UPDATE Schedule s SET s.category = :defaultCategory WHERE s.category = :targetCategory")
    int migrateCategory(
            @Param("targetCategory") Category targetCategory,
            @Param("defaultCategory") Category defaultCategory
    );
}
