package dev.mosiur.drill.problem.controller;

import dev.mosiur.drill.problem.service.ProblemCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProblemPageController {

    private final ProblemCatalogService catalog;

    public ProblemPageController(ProblemCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/problems")
    public String problems(Model model) {
        model.addAttribute("problems", catalog.listActive());
        return "problems";
    }
}
