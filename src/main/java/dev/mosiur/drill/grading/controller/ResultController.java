package dev.mosiur.drill.grading.controller;

import java.net.URI;
import java.util.Map;

import dev.mosiur.drill.grading.model.ResultView;
import dev.mosiur.drill.grading.service.FollowUpService;
import dev.mosiur.drill.grading.service.GradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/drills/{id}")
public class ResultController {

    private final GradingService grading;
    private final FollowUpService followUps;

    public ResultController(GradingService grading, FollowUpService followUps) {
        this.grading = grading;
        this.followUps = followUps;
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
        if (grading.result(id).isPending()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.noContent().header("HX-Refresh", "true").build();
    }

    @PostMapping("/regrade")
    public ResponseEntity<Void> regrade(@PathVariable long id) {
        grading.regrade(id);
        return ResponseEntity.status(303).location(URI.create("/drills/" + id + "/result")).build();
    }

    @PostMapping("/follow-up")
    public ResponseEntity<Void> submitFollowUp(@PathVariable long id, @RequestParam Map<String, String> fields) {
        followUps.submit(id, fields);
        return backToResult(id);
    }

    @PostMapping("/follow-up/regrade")
    public ResponseEntity<Void> regradeFollowUp(@PathVariable long id) {
        followUps.regrade(id);
        return backToResult(id);
    }

    @PostMapping("/finish")
    public ResponseEntity<Void> finish(@PathVariable long id) {
        followUps.skip(id);
        return backToResult(id);
    }

    private static ResponseEntity<Void> backToResult(long id) {
        return ResponseEntity.status(303).location(URI.create("/drills/" + id + "/result#follow-up")).build();
    }
}
