package dev.mosiur.drill.problem.service;

import java.util.List;

import dev.mosiur.drill.problem.model.ProblemDefinition;

public interface ProblemFileReader {

    List<ProblemDefinition> readAll();
}
