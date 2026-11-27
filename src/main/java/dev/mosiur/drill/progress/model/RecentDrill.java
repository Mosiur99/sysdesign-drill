package dev.mosiur.drill.progress.model;

import java.time.LocalDate;

public record RecentDrill(long drillId, LocalDate day, String title, boolean retry, int score) {
}
