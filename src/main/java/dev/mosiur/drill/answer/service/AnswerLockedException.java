package dev.mosiur.drill.answer.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AnswerLockedException extends RuntimeException {

    public AnswerLockedException(long drillId) {
        super("drill " + drillId + " is already submitted");
    }
}
