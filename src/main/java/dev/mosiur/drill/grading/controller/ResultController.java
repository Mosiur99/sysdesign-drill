package dev.mosiur.drill.grading.controller;

import java.net.URI;

import dev.mosiur.drill.grading.model.ResultView;
import dev.mosiur.drill.grading.service.GradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/drills/{id}")
public class ResultController {

    private final GradingService grading;

    public ResultController(GradingService grading) {
        this.grading = grading;
    }

    @GetMapping("/result")
    public String result(@PathVariable long id, Model model) {
        ResultView result = grading.result(id);
        if (result.state() == ResultView.State.NOT_SUBMITTED) {
            return "redirect:/drills/" + id;
        }
        model.addAttribute("result", result);
        return "result";
    }

    @GetMapping("/result/progress")
    public ResponseEntity<Void> progress(@PathVariable long id) {
        if (grading.result(id).isGrading()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.noContent().header("HX-Refresh", "true").build();
    }

    @PostMapping("/regrade")
    public ResponseEntity<Void> regrade(@PathVariable long id) {
        grading.regrade(id);
        return ResponseEntity.status(303).location(URI.create("/drills/" + id + "/result")).build();
    }
}
