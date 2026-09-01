package com.example.teblyserver.schedule.repository;

import com.example.teblyserver.schedule.domain.ScheduleReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleReminderRepository extends JpaRepository<ScheduleReminder, Long> {

    // 알림 후보 리마인더 조회 (앞으로 24시간 내 시작하는 일정의, 아직 안 보낸 리마인더)
    @Query("SELECT r FROM ScheduleReminder r " +
            "JOIN FETCH r.schedule s " +
            "JOIN FETCH s.user u " +
            "WHERE s.isDeleted = false " +
            "AND s.startTime BETWEEN :now AND :within24h " +
            "AND (r.lastNotifiedAt IS NULL OR r.lastNotifiedAt < :todayStart)")
    List<ScheduleReminder> findReminderCandidates(
            @Param("now") LocalDateTime now,
            @Param("within24h") LocalDateTime within24h,
            @Param("todayStart") LocalDateTime todayStart
    );
}