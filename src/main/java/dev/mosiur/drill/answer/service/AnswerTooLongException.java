package dev.mosiur.drill.answer.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AnswerTooLongException extends RuntimeException {

    public AnswerTooLongException(String section, int limit) {
        super("section '" + section + "' is longer than " + limit + " characters");
    }
}
