package dev.mosiur.drill.progress.service.impl;

import dev.mosiur.drill.drill.model.DrillFinished;
import dev.mosiur.drill.progress.domain.ProblemProgress;
import dev.mosiur.drill.progress.repository.ProblemProgressRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProgressRecorder {

    private final ProblemProgressRepository progress;

    public ProgressRecorder(ProblemProgressRepository progress) {
        this.progress = progress;
    }

    @EventListener
    public void onDrillFinished(DrillFinished event) {
        ProblemProgress problem = progress.findById(event.problemId())
                .orElseGet(() -> new ProblemProgress(event.problemId()));
        problem.record(event.finalScore(), event.day());
        progress.save(problem);
    }
}
