package dev.mosiur.drill.problem.domain;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Topic {
    STORAGE("storage"),
    CACHING("caching"),
    MESSAGING("messaging"),
    CONSISTENCY("consistency"),
    REAL_TIME("real-time"),
    SEARCH("search"),
    RATE_LIMITING("rate-limiting"),
    ID_GENERATION("id-generation"),
    PAYMENTS("payments"),
    SCHEDULING("scheduling"),
    STREAMING("streaming"),
    GEO("geo"),
    OBSERVABILITY("observability");

    private final String code;

    Topic(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static Topic fromCode(String code) {
        return Arrays.stream(values())
                .filter(topic -> topic.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown topic '" + code + "'"));
    }
}
