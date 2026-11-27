package dev.mosiur.drill.grading.service.impl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

import dev.mosiur.drill.answer.model.AnswerSubmitted;
import dev.mosiur.drill.answer.model.FollowUpSubmitted;
import dev.mosiur.drill.grading.service.FollowUpService;
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
    private final FollowUpService followUps;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public GradingWorker(GradingService grading, FollowUpService followUps) {
        this.grading = grading;
        this.followUps = followUps;
    }

    @Async
    @TransactionalEventListener
    public void onSubmitted(AnswerSubmitted event) {
        run(event.drillId());
    }

    @Async
    @TransactionalEventListener
    public void onFollowUpSubmitted(FollowUpSubmitted event) {
        run(event.drillId(), followUps::grade);
    }

    @Scheduled(fixedDelayString = "${drill.grading.sweep-interval}")
    public void gradeStuckDrills() {
        grading.stuckDrills().forEach(this::run);
        followUps.stuckDrills().forEach(drillId -> run(drillId, followUps::grade));
    }

    void run(long drillId) {
        run(drillId, grading::grade);
    }

    private void run(long drillId, LongConsumer job) {
        if (!inFlight.add(drillId)) {
            return;
        }
        try {
            job.accept(drillId);
        } catch (RuntimeException e) {
            log.error("Grading drill {} failed unexpectedly", drillId, e);
        } finally {
            inFlight.remove(drillId);
        }
    }
}
