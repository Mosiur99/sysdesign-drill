package dev.mosiur.drill.progress.controller;

import java.security.Principal;

import dev.mosiur.drill.progress.service.ProgressService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class StreakAdvice {

    private final ProgressService progress;

    public StreakAdvice(ProgressService progress) {
        this.progress = progress;
    }

    @ModelAttribute("streak")
    public Integer streak(Principal principal) {
        return principal == null ? null : progress.streak().current();
    }
}
