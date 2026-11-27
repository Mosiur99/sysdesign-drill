package dev.mosiur.drill.answer.model;

import dev.mosiur.drill.problem.domain.Dimension;

public enum SectionGuide {
    REQUIREMENTS(Dimension.REQUIREMENTS, "Requirements",
            "Functional and non-functional requirements, what is out of scope, and the numbers you would ask for.",
            7, false),
    ESTIMATION(Dimension.ESTIMATION, "Estimation",
            "Traffic, storage and bandwidth, with your assumptions. Use the numbers later.", 5, false),
    API(Dimension.API, "API",
            "Endpoints or messages, request and response, idempotency and paging where they matter.", 6, true),
    DATA_MODEL(Dimension.DATA_MODEL, "Data model",
            "Entities, keys, access patterns, storage choice and partition key.", 6, false),
    ARCHITECTURE(Dimension.ARCHITECTURE, "Architecture",
            "Components and data flow. Write a Mermaid flowchart to draw it; the preview updates as you type.",
            11, true),
    DEEP_DIVE(Dimension.DEEP_DIVE, "Deep dive", "The hardest part of this problem, solved in detail.", 8, false),
    SCALE_FAILURE(Dimension.SCALE_FAILURE, "Scale and failure",
            "Bottlenecks, replication, and what happens when each part fails or slows down.", 6, false),
    TRADEOFFS(Dimension.TRADEOFFS, "Trade-offs",
            "The choices you made, what you gave up, and what you would change at ten times the load.", 5, false);

    private final Dimension dimension;
    private final String title;
    private final String hint;
    private final int rows;
    private final boolean code;

    SectionGuide(Dimension dimension, String title, String hint, int rows, boolean code) {
        this.dimension = dimension;
        this.title = title;
        this.hint = hint;
        this.rows = rows;
        this.code = code;
    }

    public String key() {
        return dimension.code();
    }

    public String title() {
        return title;
    }

    public String hint() {
        return hint;
    }

    public int rows() {
        return rows;
    }

    public boolean code() {
        return code;
    }

    public boolean diagram() {
        return dimension == Dimension.ARCHITECTURE;
    }
}
