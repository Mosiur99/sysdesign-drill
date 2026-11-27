package dev.mosiur.drill.grading.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class RegradeNotAllowedException extends RuntimeException {

    public RegradeNotAllowedException(long drillId) {
        super("drill " + drillId + " has no submitted answer to grade");
    }
}
