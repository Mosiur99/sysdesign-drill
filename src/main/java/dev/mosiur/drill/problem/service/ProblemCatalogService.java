package dev.mosiur.drill.problem.service;

import java.util.List;

import dev.mosiur.drill.problem.model.CatalogSyncResult;
import dev.mosiur.drill.problem.model.ProblemDefinition;
import dev.mosiur.drill.problem.model.ProblemSummary;

public interface ProblemCatalogService {

    CatalogSyncResult sync(List<ProblemDefinition> definitions);

    List<ProblemSummary> listActive();
}
