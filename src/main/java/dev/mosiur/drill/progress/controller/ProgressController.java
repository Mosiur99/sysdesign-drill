package dev.mosiur.drill.progress.controller;

import dev.mosiur.drill.progress.service.ProgressService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProgressController {

    private final ProgressService progress;

    public ProgressController(ProgressService progress) {
        this.progress = progress;
    }

    @GetMapping("/progress")
    public String progress(Model model) {
        model.addAttribute("progress", progress.progress());
        return "progress";
    }
}
