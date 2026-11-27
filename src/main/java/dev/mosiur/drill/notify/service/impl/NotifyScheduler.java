package dev.mosiur.drill.notify.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.IsoFields;

import dev.mosiur.drill.drill.domain.DrillStatus;
import dev.mosiur.drill.drill.service.DailyDrillService;
import dev.mosiur.drill.notify.TelegramProperties;
import dev.mosiur.drill.notify.domain.NotificationKind;
import dev.mosiur.drill.notify.repository.NotificationRepository;
import dev.mosiur.drill.notify.service.AppUrl;
import dev.mosiur.drill.progress.service.ProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotifyScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotifyScheduler.class);

    private final DailyDrillService dailyDrills;
    private final ProgressService progress;
    private final NotificationRepository notifications;
    private final TelegramProperties telegram;
    private final Clock clock;
    private final AppUrl appUrl;

    public NotifyScheduler(DailyDrillService dailyDrills, ProgressService progress,
                           NotificationRepository notifications, TelegramProperties telegram, Clock clock,
                           AppUrl appUrl) {
        this.appUrl = appUrl;
        this.dailyDrills = dailyDrills;
        this.progress = progress;
        this.notifications = notifications;
        this.telegram = telegram;
        this.clock = clock;
    }

    @Scheduled(cron = "${drill.notify.daily-cron}", zone = "${drill.zone}")
    public void daily() {
        if (enabled()) {
            dailyDrills.assignToday().ifPresent(drill -> enqueue(NotificationKind.DAILY, "daily:" + drill.day(),
                    MessageFormatter.daily(drill, appUrl.current())));
        }
    }

    @Scheduled(cron = "${drill.notify.reminder-cron}", zone = "${drill.zone}")
    public void reminder() {
        if (enabled()) {
            dailyDrills.assignToday()
                    .filter(drill -> drill.status() == DrillStatus.ASSIGNED || drill.status() == DrillStatus.IN_PROGRESS)
                    .ifPresent(drill -> enqueue(NotificationKind.REMINDER, "reminder:" + drill.day(),
                            MessageFormatter.reminder(drill, appUrl.current())));
        }
    }

    @Scheduled(cron = "${drill.notify.weekly-cron}", zone = "${drill.zone}")
    public void weekly() {
        if (enabled()) {
            LocalDate today = LocalDate.now(clock);
            String week = today.get(IsoFields.WEEK_BASED_YEAR) + "-W" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            enqueue(NotificationKind.WEEKLY, "weekly:" + week,
                    MessageFormatter.weekly(progress.weekly(), appUrl.current()));
        }
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    public void announceAddress() {
        if (enabled()) {
            appUrl.tunnel().ifPresent(url -> enqueue(NotificationKind.ADDRESS, "address:" + url,
                    MessageFormatter.address(url)));
        }
    }

    private boolean enabled() {
        if (!telegram.isConfigured()) {
            log.debug("Telegram is not configured, so no notification was queued");
        }
        return telegram.isConfigured();
    }

    private void enqueue(NotificationKind kind, String dedupeKey, String payload) {
        if (notifications.enqueue(kind, dedupeKey, payload, clock.instant())) {
            log.info("Queued {} notification {}", kind, dedupeKey);
        }
    }
}
