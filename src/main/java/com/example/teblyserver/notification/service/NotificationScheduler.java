package com.example.teblyserver.notification.service;

import com.example.teblyserver.notification.domain.NotificationType;
import com.example.teblyserver.schedule.domain.Schedule;
import com.example.teblyserver.schedule.domain.ScheduleReminder;
import com.example.teblyserver.schedule.repository.ScheduleReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final ScheduleReminderRepository scheduleReminderRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 * * * * *") // 매 1분마다 실행
    @Transactional
    public void sendScheduleNotifications() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime within24h = now.plusHours(24);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        List<ScheduleReminder> candidates = scheduleReminderRepository.findReminderCandidates(now, within24h, todayStart);

        for (ScheduleReminder reminder : candidates) {
            Schedule schedule = reminder.getSchedule();
            LocalDateTime notifyTime = schedule.getStartTime().minusMinutes(reminder.getLeadMinutes());

            if (!notifyTime.isAfter(now)) {
                notificationService.send(
                        schedule.getUser(),
                        NotificationType.REMINDER,
                        schedule.getTitle(),
                        schedule.getTitle() + " 일정이 " + reminder.getLeadMinutes() + "분 후에 시작돼요!",
                        "/schedules/" + schedule.getId(),
                        schedule.getCategory().getId(),
                        schedule.getId(),
                        schedule.getTitle(),
                        null,
                        null,
                        schedule.getStartTime()
                );

                reminder.updateLastNotifiedAt(now);

                log.info("알림 발송: userId={}, scheduleId={}, leadMinutes={}",
                        schedule.getUser().getId(), schedule.getId(), reminder.getLeadMinutes());
            }
        }
    }
}