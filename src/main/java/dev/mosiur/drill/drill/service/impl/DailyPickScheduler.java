package dev.mosiur.drill.drill.service.impl;

import dev.mosiur.drill.drill.service.DailyDrillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyPickScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyPickScheduler.class);

    private final DailyDrillService dailyDrills;

    public DailyPickScheduler(DailyDrillService dailyDrills) {
        this.dailyDrills = dailyDrills;
    }

    @Scheduled(cron = "${drill.pick.cron}", zone = "${drill.zone}")
    public void assignToday() {
        dailyDrills.assignToday().ifPresentOrElse(
                drill -> log.info("Drill for {}: {} ({})", drill.day(), drill.title(), drill.kind()),
                () -> log.warn("No active problems, so no drill was assigned"));
    }
}
