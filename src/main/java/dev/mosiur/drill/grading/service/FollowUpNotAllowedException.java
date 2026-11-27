package dev.mosiur.drill.grading.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class FollowUpNotAllowedException extends RuntimeException {

    public FollowUpNotAllowedException(long drillId, String action) {
        super("drill " + drillId + ": cannot " + action + " now");
    }
}
