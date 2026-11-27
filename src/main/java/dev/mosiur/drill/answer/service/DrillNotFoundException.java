package dev.mosiur.drill.answer.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DrillNotFoundException extends RuntimeException {

    public DrillNotFoundException(long drillId) {
        super("drill " + drillId + " does not exist");
    }
}
