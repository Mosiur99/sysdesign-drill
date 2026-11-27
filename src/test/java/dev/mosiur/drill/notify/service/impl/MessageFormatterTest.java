package dev.mosiur.drill.notify.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import dev.mosiur.drill.drill.domain.DrillKind;
import dev.mosiur.drill.drill.domain.DrillStatus;
import dev.mosiur.drill.drill.model.TodayDrill;
import dev.mosiur.drill.progress.model.NextRetry;
import dev.mosiur.drill.progress.model.SkillTrend;
import dev.mosiur.drill.progress.model.Streak;
import dev.mosiur.drill.progress.model.TopicStrength;
import dev.mosiur.drill.progress.model.WeeklyReport;
import org.junit.jupiter.api.Test;

class MessageFormatterTest {

    private static final TodayDrill DRILL = new TodayDrill(12, LocalDate.of(2026, 11, 28), DrillKind.RETRY,
            DrillStatus.ASSIGNED, "Design a <cache> & CDN", "Prompt", 2, List.of("caching", "storage"));

    @Test
    void dailyMessageHasTitleDetailsAndLink() {
        String text = MessageFormatter.daily(DRILL, "https://drill.example/");

        assertThat(text).isEqualTo("""
                <b>Today's problem</b>
                Design a &lt;cache&gt; &amp; CDN
                Retry · Medium · caching, storage

                <a href="https://drill.example/drills/12">Start answer</a> · 45 minutes""");
    }

    @Test
    void reminderDependsOnWhetherADraftExists() {
        TodayDrill started = new TodayDrill(12, DRILL.day(), DrillKind.NEW, DrillStatus.IN_PROGRESS, "Design X",
                "p", 1, List.of());

        assertThat(MessageFormatter.reminder(DRILL, "http://localhost:8080")).contains("haven't started");
        assertThat(MessageFormatter.reminder(started, "http://localhost:8080")).contains("Your draft is saved")
                .contains("http://localhost:8080/drills/12");
    }

    @Test
    void weeklyReportSummarisesTheWeek() {
        WeeklyReport report = new WeeklyReport(5, 64, new Streak(7, 9),
                List.of(new SkillTrend("API", 3.2, null, ""), new SkillTrend("Estimation", 1.6, null, "")),
                new TopicStrength("caching", 3, 72), new TopicStrength("payments", 2, 48),
                List.of(new NextRetry("Design a payment ledger", LocalDate.of(2026, 11, 30), new BigDecimal("48.4"))));

        String text = MessageFormatter.weekly(report, "http://localhost:8080");

        assertThat(text).startsWith("<b>Your week</b>\n5 drills, average score 64.\nStreak: 7 days (longest 9).")
                .contains("API 3.2\nEstimation 1.6")
                .contains("Strongest topic: caching (72)\nWeakest topic: payments (48)")
                .contains("Mon 30 Nov: Design a payment ledger (last 48)")
                .endsWith("<a href=\"http://localhost:8080/progress\">Open progress</a>");
    }

    @Test
    void leavesOutTopicsWhenTheyAllScoreTheSame() {
        TopicStrength only = new TopicStrength("payments", 1, 97);
        String text = MessageFormatter.weekly(new WeeklyReport(1, 97, new Streak(1, 1), List.of(), only, only,
                List.of()), "http://localhost:8080");

        assertThat(text).doesNotContain("Strongest topic").doesNotContain("Weakest topic");
    }

    @Test
    void quietWeekStillSendsTheStreak() {
        String text = MessageFormatter.weekly(new WeeklyReport(0, null, new Streak(0, 3), List.of(), null, null,
                List.of()), "http://localhost:8080");

        assertThat(text).contains("No graded drills in the last 7 days.").contains("Streak: 0 days (longest 3).")
                .doesNotContain("Strongest").doesNotContain("Retries");
    }
}
