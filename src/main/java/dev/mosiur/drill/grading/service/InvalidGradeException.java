package dev.mosiur.drill.grading.service;

public class InvalidGradeException extends RuntimeException {

    public InvalidGradeException(String message) {
        super(message);
    }
}
