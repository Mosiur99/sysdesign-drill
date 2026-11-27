package dev.mosiur.drill.answer.controller;

import java.net.URI;
import java.util.Map;

import dev.mosiur.drill.answer.model.EditorView;
import dev.mosiur.drill.answer.service.AnswerService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/drills/{id}")
public class AnswerController {

    private final AnswerService answers;

    public AnswerController(AnswerService answers) {
        this.answers = answers;
    }

    @GetMapping
    public String editor(@PathVariable long id, Model model) {
        EditorView editor = answers.open(id);
        if (!editor.editable()) {
            return "redirect:/drills/" + id + "/result";
        }
        model.addAttribute("editor", editor);
        return "editor";
    }

    @PutMapping("/answer")
    public ResponseEntity<Void> saveDraft(@PathVariable long id, @RequestParam Map<String, String> fields) {
        answers.saveDraft(id, fields);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/submit")
    public ResponseEntity<Void> submit(@PathVariable long id, @RequestParam Map<String, String> fields) {
        answers.submit(id, fields);
        return ResponseEntity.status(303).location(URI.create("/drills/" + id + "/result")).build();
    }

    @GetMapping("/result")
    public String result(@PathVariable long id, Model model) {
        model.addAttribute("submission", answers.submission(id));
        return "result";
    }
}
