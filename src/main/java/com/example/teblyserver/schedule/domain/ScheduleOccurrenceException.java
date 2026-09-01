package com.example.teblyserver.schedule.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "schedule_occurrence_exceptions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_schedule_occurrence_exception",
                        columnNames = {"schedule_id", "occurrence_start_time"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleOccurrenceException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(name = "occurrence_start_time", nullable = false)
    private LocalDateTime occurrenceStartTime;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ScheduleOccurrenceException create(
            Schedule schedule,
            LocalDateTime occurrenceStartTime
    ) {
        ScheduleOccurrenceException exception =
                new ScheduleOccurrenceException();

        exception.schedule = schedule;
        exception.occurrenceStartTime = occurrenceStartTime;

        return exception;
    }
}
