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
            "AND (" +
            // 조건 1. 단건(NONE) 일정이면서 해당 기간에 포함될 때
            "(s.repeatType = 'NONE' AND s.startTime <= :endDateTime AND s.endTime >= :startDateTime) " +
            "OR " +
            // 조건 2. 반복 일정(WEEKLY 등)이면서, 조회하려는 기간의 '끝' 이전에 시작된 적이 있는 모든 일정
            "(s.repeatType != 'NONE' AND s.startTime <= :endDateTime)" +
            ")")
    List<Schedule> findSchedulesWithinRange(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // 알림 후보 일정 조회 (앞으로 24시간 내 시작하는, 아직 오늘 알림 안 보낸 일정)
    @Query("SELECT s FROM Schedule s " +
            "JOIN FETCH s.user u " +
            "WHERE s.isDeleted = false " +
            "AND s.notificationLeadMinutes IS NOT NULL " +
            "AND s.startTime BETWEEN :now AND :within24h " +
            "AND (s.lastNotifiedAt IS NULL OR s.lastNotifiedAt < :todayStart)")
    List<Schedule> findNotificationCandidates(
            @Param("now") LocalDateTime now,
            @Param("within24h") LocalDateTime within24h,
            @Param("todayStart") LocalDateTime todayStart
    );
    // 타겟 카테고리를 가진 모든 일정을 기본 카테고리로 일괄 업데이트
    @Modifying(clearAutomatically = true) // 벌크 연산 후 영속성 컨텍스트(캐시)를 비워주는 필수 옵션
    @Query("UPDATE Schedule s SET s.category = :defaultCategory WHERE s.category = :targetCategory")
    int migrateCategory(
            @Param("targetCategory") Category targetCategory,
            @Param("defaultCategory") Category defaultCategory
    );
}
