package dev.mosiur.drill.problem.domain;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Dimension {
    REQUIREMENTS("requirements"),
    ESTIMATION("estimation"),
    API("api"),
    DATA_MODEL("data_model"),
    ARCHITECTURE("architecture"),
    DEEP_DIVE("deep_dive"),
    SCALE_FAILURE("scale_failure"),
    TRADEOFFS("tradeoffs");

    private final String code;

    Dimension(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static Dimension fromCode(String code) {
        return Arrays.stream(values())
                .filter(dimension -> dimension.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown dimension '" + code + "'"));
    }
}
