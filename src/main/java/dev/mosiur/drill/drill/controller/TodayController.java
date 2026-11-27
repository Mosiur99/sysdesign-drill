package dev.mosiur.drill.drill.controller;

import dev.mosiur.drill.drill.service.DailyDrillService;
import dev.mosiur.drill.progress.service.ProgressService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TodayController {

    private final DailyDrillService dailyDrills;
    private final ProgressService progress;

    public TodayController(DailyDrillService dailyDrills, ProgressService progress) {
        this.dailyDrills = dailyDrills;
        this.progress = progress;
    }

    @GetMapping("/")
    public String today(Model model) {
        dailyDrills.assignToday().ifPresent(drill -> model.addAttribute("drill", drill));
        model.addAttribute("summary", progress.today());
        return "today";
    }
}
