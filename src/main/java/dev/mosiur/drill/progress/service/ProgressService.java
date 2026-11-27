package dev.mosiur.drill.progress.service;

import dev.mosiur.drill.progress.model.ProgressView;
import dev.mosiur.drill.progress.model.Streak;
import dev.mosiur.drill.progress.model.TodaySummary;
import dev.mosiur.drill.progress.model.WeeklyReport;

public interface ProgressService {

    ProgressView progress();

    TodaySummary today();

    Streak streak();

    WeeklyReport weekly();
}
