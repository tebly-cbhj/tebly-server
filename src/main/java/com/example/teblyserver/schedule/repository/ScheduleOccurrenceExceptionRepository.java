package com.example.teblyserver.schedule.repository;

import com.example.teblyserver.schedule.domain.ScheduleOccurrenceException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ScheduleOccurrenceExceptionRepository extends JpaRepository<ScheduleOccurrenceException, Long> {

    boolean existsBySchedule_IdAndOccurrenceStartTime(
            Long scheduleId,
            LocalDateTime occurrenceStartTime
    );

    List<ScheduleOccurrenceException> findAllBySchedule_IdIn(
            Collection<Long> scheduleIds
    );

    void deleteAllBySchedule_Id(Long scheduleId);
}
