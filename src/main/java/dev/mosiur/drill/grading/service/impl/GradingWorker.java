package dev.mosiur.drill.grading.service.impl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.mosiur.drill.answer.model.AnswerSubmitted;
import dev.mosiur.drill.grading.service.GradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class GradingWorker {

    private static final Logger log = LoggerFactory.getLogger(GradingWorker.class);

    private final GradingService grading;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public GradingWorker(GradingService grading) {
        this.grading = grading;
    }

    @Async
    @TransactionalEventListener
    public void onSubmitted(AnswerSubmitted event) {
        run(event.drillId());
    }

    @Scheduled(fixedDelayString = "${drill.grading.sweep-interval}")
    public void gradeStuckDrills() {
        grading.stuckDrills().forEach(this::run);
    }

    void run(long drillId) {
        if (!inFlight.add(drillId)) {
            return;
        }
        try {
            grading.grade(drillId);
        } catch (RuntimeException e) {
            log.error("Grading drill {} failed unexpectedly", drillId, e);
        } finally {
            inFlight.remove(drillId);
        }
    }
}
