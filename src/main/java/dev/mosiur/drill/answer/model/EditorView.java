package dev.mosiur.drill.answer.model;

import java.time.Instant;
import java.util.List;

public record EditorView(long drillId, String title, String prompt, Instant startedAt, boolean editable,
                         List<EditorSection> sections) {

    public long startedAtMillis() {
        return startedAt.toEpochMilli();
    }

    public long writtenCount() {
        return sections.stream().filter(section -> section.words() > 0).count();
    }
}
