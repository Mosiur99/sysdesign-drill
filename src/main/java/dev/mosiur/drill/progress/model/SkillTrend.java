package dev.mosiur.drill.progress.model;

import java.util.Locale;

public record SkillTrend(String title, double average, Double delta, String points) {

    public String averageText() {
        return String.format(Locale.ROOT, "%.1f", average);
    }

    public String deltaText() {
        return delta == null ? "" : (delta >= 0 ? "+" : "−") + String.format(Locale.ROOT, "%.1f", Math.abs(delta));
    }

    public boolean falling() {
        return delta != null && delta < 0;
    }

    public String label() {
        String trend = delta == null ? "" : delta >= 0 ? ", up " + String.format(Locale.ROOT, "%.1f", delta)
                : ", down " + String.format(Locale.ROOT, "%.1f", -delta);
        return title + ": average " + averageText() + " of 4" + trend;
    }
}
