package dev.mosiur.drill.drill.model;

import java.time.LocalDate;
import java.util.List;

import dev.mosiur.drill.drill.domain.Drill;
import dev.mosiur.drill.drill.domain.DrillKind;
import dev.mosiur.drill.drill.domain.DrillStatus;
import dev.mosiur.drill.problem.domain.Problem;
import dev.mosiur.drill.problem.domain.Topic;

public record TodayDrill(long id, LocalDate day, DrillKind kind, DrillStatus status,
                         String title, String prompt, int difficulty, List<String> topics) {

    public static TodayDrill from(Drill drill) {
        Problem problem = drill.getProblem();
        List<String> topics = problem.getTopics().stream().sorted().map(Topic::code).toList();
        return new TodayDrill(drill.getId(), drill.getScheduledFor(), drill.getKind(), drill.getStatus(),
                problem.getTitle(), problem.getPrompt(), problem.getDifficulty(), topics);
    }
}
