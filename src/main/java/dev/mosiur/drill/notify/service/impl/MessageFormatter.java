package dev.mosiur.drill.notify.service.impl;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import dev.mosiur.drill.drill.domain.DrillKind;
import dev.mosiur.drill.drill.domain.DrillStatus;
import dev.mosiur.drill.drill.model.TodayDrill;
import dev.mosiur.drill.progress.model.NextRetry;
import dev.mosiur.drill.progress.model.SkillTrend;
import dev.mosiur.drill.progress.model.WeeklyReport;

final class MessageFormatter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final List<String> DIFFICULTY = List.of("", "Easy", "Medium", "Hard");

    private MessageFormatter() {
    }

    static String daily(TodayDrill drill, String appUrl) {
        String kind = drill.kind() == DrillKind.RETRY ? "Retry" : "New problem";
        return """
                <b>Today's problem</b>
                %s
                %s · %s · %s

                <a href="%s">Start answer</a> · 45 minutes""".formatted(escape(drill.title()), kind,
                DIFFICULTY.get(drill.difficulty()), escape(String.join(", ", drill.topics())),
                link(appUrl, "/drills/" + drill.id()));
    }

    static String reminder(TodayDrill drill, String appUrl) {
        String state = drill.status() == DrillStatus.IN_PROGRESS
                ? "Your draft is saved. Submit it before midnight to keep your streak."
                : "You haven't started today's problem yet. Submit before midnight to keep your streak.";
        return """
                <b>Reminder</b>
                %s
                %s

                <a href="%s">Open the editor</a>""".formatted(escape(drill.title()), state,
                link(appUrl, "/drills/" + drill.id()));
    }

    static String weekly(WeeklyReport report, String appUrl) {
        StringBuilder text = new StringBuilder("<b>Your week</b>\n");
        if (report.drills() == 0) {
            text.append("No graded drills in the last 7 days.\n");
        } else {
            text.append(report.drills()).append(report.drills() == 1 ? " drill" : " drills")
                    .append(", average score ").append(report.average()).append(".\n");
        }
        text.append("Streak: ").append(report.streak().current()).append(report.streak().current() == 1 ? " day" : " days")
                .append(" (longest ").append(report.streak().longest()).append(").\n");
        if (!report.sections().isEmpty()) {
            text.append("\n<b>Sections</b> (out of 4)\n").append(report.sections().stream()
                    .map(MessageFormatter::section).collect(Collectors.joining("\n"))).append('\n');
        }
        if (report.strongest() != null && report.strongest().average() > report.weakest().average()) {
            text.append("\nStrongest topic: ").append(escape(report.strongest().name())).append(" (")
                    .append(report.strongest().average()).append(")\n")
                    .append("Weakest topic: ").append(escape(report.weakest().name())).append(" (")
                    .append(report.weakest().average()).append(")\n");
        }
        if (!report.upcomingRetries().isEmpty()) {
            text.append("\n<b>Retries next week</b>\n").append(report.upcomingRetries().stream()
                    .map(MessageFormatter::retry).collect(Collectors.joining("\n"))).append('\n');
        }
        return text.append("\n<a href=\"").append(link(appUrl, "/progress")).append("\">Open progress</a>").toString();
    }

    private static String section(SkillTrend skill) {
        return escape(skill.title()) + " " + skill.averageText();
    }

    private static String retry(NextRetry retry) {
        return DAY.format(retry.due()) + ": " + escape(retry.title()) + " (last " + retry.lastScoreRounded() + ")";
    }

    private static String link(String appUrl, String path) {
        return escape(appUrl.replaceAll("/+$", "") + path);
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
