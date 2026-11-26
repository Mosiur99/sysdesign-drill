package dev.mosiur.drill.problem.service;

import java.util.List;

public class InvalidProblemFileException extends RuntimeException {

    private final List<String> errors;

    public InvalidProblemFileException(List<String> errors) {
        super("Problem bank is invalid:\n  " + String.join("\n  ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
