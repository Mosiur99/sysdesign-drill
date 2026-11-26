package dev.mosiur.drill.problem.controller;

import java.util.List;

import dev.mosiur.drill.problem.model.ProblemSummary;
import dev.mosiur.drill.problem.service.ProblemCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemCatalogService catalog;

    public ProblemController(ProblemCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ProblemSummary> list() {
        return catalog.listActive();
    }
}
