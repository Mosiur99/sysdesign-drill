package dev.mosiur.drill.drill.controller;

import dev.mosiur.drill.drill.service.DailyDrillService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TodayController {

    private final DailyDrillService dailyDrills;

    public TodayController(DailyDrillService dailyDrills) {
        this.dailyDrills = dailyDrills;
    }

    @GetMapping("/")
    public String today(Model model) {
        dailyDrills.assignToday().ifPresent(drill -> model.addAttribute("drill", drill));
        return "today";
    }
}
