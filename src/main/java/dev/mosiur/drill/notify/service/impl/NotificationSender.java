package dev.mosiur.drill.notify.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import dev.mosiur.drill.notify.model.ClaimedNotification;
import dev.mosiur.drill.notify.model.SendResult;
import dev.mosiur.drill.notify.repository.NotificationRepository;
import dev.mosiur.drill.notify.service.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationSender {

    static final int MAX_ATTEMPTS = 3;
    static final int BATCH = 10;
    static final Duration BACKOFF = Duration.ofSeconds(30);
    static final Duration STALE_CLAIM = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final NotificationRepository notifications;
    private final TelegramClient telegram;
    private final Clock clock;

    public NotificationSender(NotificationRepository notifications, TelegramClient telegram, Clock clock) {
        this.notifications = notifications;
        this.telegram = telegram;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${drill.notify.send-interval}")
    public void sendDue() {
        Instant now = clock.instant();
        notifications.releaseStale(now.minus(STALE_CLAIM));
        for (ClaimedNotification notification : notifications.claimDue(now, BATCH)) {
            SendResult result = telegram.send(notification.payload());
            switch (result.outcome()) {
                case SENT -> {
                    notifications.markSent(notification.id(), clock.instant());
                    log.info("Notification {} sent", notification.id());
                }
                case REJECTED -> {
                    notifications.markFailed(notification.id(), result.detail());
                    log.warn("Notification {} rejected: {}", notification.id(), result.detail());
                }
                case RETRY -> retry(notification, result);
            }
        }
    }

    private void retry(ClaimedNotification notification, SendResult result) {
        if (notification.attempts() >= MAX_ATTEMPTS) {
            notifications.markFailed(notification.id(), result.detail());
            log.warn("Notification {} failed after {} attempts: {}", notification.id(), notification.attempts(),
                    result.detail());
            return;
        }
        Duration wait = result.retryAfter() != null ? result.retryAfter()
                : BACKOFF.multipliedBy(notification.attempts());
        notifications.markRetry(notification.id(), clock.instant().plus(wait), result.detail());
        log.info("Notification {} will retry in {} s: {}", notification.id(), wait.toSeconds(), result.detail());
    }
}
