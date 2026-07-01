package com.example.teblyserver.notification.service;

import com.example.teblyserver.notification.domain.NotificationType;
import com.example.teblyserver.schedule.domain.Schedule;
import com.example.teblyserver.schedule.repository.ScheduleRepository;
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

    private final ScheduleRepository scheduleRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 * * * * *") // 매 1분마다 실행
    @Transactional
    public void sendScheduleNotifications() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime within24h = now.plusHours(24);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        List<Schedule> candidates = scheduleRepository.findNotificationCandidates(now, within24h, todayStart);

        for (Schedule schedule : candidates) {
            LocalDateTime notifyTime = schedule.getCalculatedNotificationTime();
            if (notifyTime == null) {
                continue;
            }

            if (!notifyTime.isAfter(now)) {
                notificationService.send(
                        schedule.getUser(),
                        NotificationType.REMINDER,
                        schedule.getTitle(),
                        schedule.getTitle() + " 일정이 " + schedule.getNotificationLeadMinutes() + "분 후에 시작돼요!",
                        "/schedules/" + schedule.getId()
                );

                schedule.updateLastNotifiedAt(now);

                log.info("알림 발송: userId={}, scheduleId={}", schedule.getUser().getId(), schedule.getId());
            }
        }
    }
}
