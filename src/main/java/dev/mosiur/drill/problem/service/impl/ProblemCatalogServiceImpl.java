package dev.mosiur.drill.problem.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.mosiur.drill.problem.domain.Problem;
import dev.mosiur.drill.problem.model.CatalogSyncResult;
import dev.mosiur.drill.problem.model.KeyPointDefinition;
import dev.mosiur.drill.problem.model.ProblemDefinition;
import dev.mosiur.drill.problem.model.ProblemSummary;
import dev.mosiur.drill.problem.repository.ProblemRepository;
import dev.mosiur.drill.problem.service.ProblemCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemCatalogServiceImpl implements ProblemCatalogService {

    private static final Comparator<Problem> LISTING_ORDER = Comparator.comparingInt(Problem::getDifficulty)
            .thenComparing(Problem::getTitle, String.CASE_INSENSITIVE_ORDER);

    private final ProblemRepository problems;

    public ProblemCatalogServiceImpl(ProblemRepository problems) {
        this.problems = problems;
    }

    @Override
    @Transactional
    public CatalogSyncResult sync(List<ProblemDefinition> definitions) {
        Map<String, Problem> existing = problems.findAll().stream()
                .collect(Collectors.toMap(Problem::getSlug, Function.identity()));
        int created = 0;
        for (ProblemDefinition definition : definitions) {
            Problem problem = existing.remove(definition.slug());
            if (problem == null) {
                problem = new Problem(definition.slug());
                created++;
            }
            apply(definition, problem);
            problems.save(problem);
        }
        long deactivated = existing.values().stream().filter(Problem::isActive).count();
        existing.values().forEach(Problem::deactivate);
        return new CatalogSyncResult(created, definitions.size() - created, (int) deactivated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemSummary> listActive() {
        return problems.findByActiveTrue().stream()
                .sorted(LISTING_ORDER)
                .map(ProblemSummary::from)
                .toList();
    }

    private void apply(ProblemDefinition definition, Problem problem) {
        problem.update(definition.title(), definition.prompt().strip(), definition.difficulty(), definition.topics());
        definition.keyPoints().forEach(keyPoint ->
                problem.putKeyPoint(keyPoint.id(), keyPoint.dimension(), keyPoint.text().strip()));
        Set<String> codes = definition.keyPoints().stream()
                .map(KeyPointDefinition::id)
                .collect(Collectors.toSet());
        problem.retainKeyPoints(codes);
    }
}
