package dev.mosiur.drill.grading.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.mosiur.drill.answer.model.SectionGuide;
import dev.mosiur.drill.grading.domain.DimensionScore;
import dev.mosiur.drill.grading.domain.Grade;
import dev.mosiur.drill.grading.domain.GradeStatus;
import dev.mosiur.drill.grading.model.ChecklistPoint;
import dev.mosiur.drill.grading.model.GradingInput;
import dev.mosiur.drill.llm.LlmProperties;
import dev.mosiur.drill.llm.service.impl.LlmClientImpl;
import dev.mosiur.drill.problem.model.ProblemDefinition;
import dev.mosiur.drill.problem.service.impl.ProblemFileReaderImpl;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class GraderEval {

    private static final int RUNS = Integer.getInteger("eval.runs", 2);
    private static final String ONLY = System.getProperty("eval.cases", "");
    private static final int TOKENS_PER_MINUTE = Integer.getInteger("eval.tpm", 7_500);
    private static final int RATE_LIMIT_RETRIES = 3;

    record EvalCase(String problem, String level, List<Integer> band, Map<String, String> sections) {

        String name() {
            return problem + "/" + level;
        }

        boolean inBand(double score) {
            return score >= band.get(0) && score <= band.get(1);
        }
    }

    private final JsonMapper json = JsonMapper.builder().build();
    private record Spend(Instant at, int tokens) {
    }

    private final Deque<Spend> spent = new ArrayDeque<>();

    @Test
    void gradesLandInTheirBandsAndAgreeAcrossRuns() throws IOException {
        LlmProperties properties = new LlmProperties(
                env("GROQ_BASE_URL", "https://api.groq.com/openai/v1"), System.getenv("GROQ_API_KEY"),
                env("GROQ_MODEL", "openai/gpt-oss-120b"), env("GROQ_REASONING_EFFORT", "low"),
                Duration.ofSeconds(5), Duration.ofSeconds(120), Duration.ofSeconds(30), 3);
        GradingServiceImpl grader = new GradingServiceImpl(null, null, null,
                new LlmClientImpl(properties, RestClient.builder(), json), new GradingPrompt(json),
                new GradeValidator(), null, null, Clock.systemUTC(), null);
        Map<String, ProblemDefinition> problems = new ProblemFileReaderImpl(new PathMatchingResourcePatternResolver(),
                Validation.buildDefaultValidatorFactory().getValidator(), "classpath:problems/*.yaml")
                .readAll().stream().collect(Collectors.toMap(ProblemDefinition::slug, Function.identity()));

        List<String> rows = new ArrayList<>();
        int inBand = 0;
        int worstDifference = 0;
        long tokens = 0;
        List<EvalCase> cases = cases();
        for (EvalCase evalCase : cases) {
            GradingInput input = input(evalCase, problems.get(evalCase.problem()));
            List<Grade> grades = new ArrayList<>();
            for (int run = 1; run <= RUNS; run++) {
                Grade grade = grade(grader, input);
                assertThat(grade.getStatus()).as(evalCase.name() + " run " + run + ": " + grade.getFailureDetail())
                        .isEqualTo(GradeStatus.OK);
                grades.add(grade);
                tokens += grade.getPromptTokens() + grade.getCompletionTokens();
            }
            double mean = grades.stream().mapToDouble(grade -> grade.getOverall().doubleValue()).average().orElseThrow();
            boolean ok = evalCase.inBand(mean);
            int difference = maxDimensionDifference(grades);
            inBand += ok ? 1 : 0;
            worstDifference = Math.max(worstDifference, difference);
            rows.add("| %s | %d-%d | %s | %.0f | %s | %s | %d | %d |".formatted(evalCase.name(), evalCase.band().get(0),
                    evalCase.band().get(1), grades.stream().map(grade -> "%.0f".formatted(grade.getOverall()))
                            .collect(Collectors.joining(" / ")), mean, ok ? "yes" : "**no**",
                    grades.stream().map(GraderEval::sectionScores).collect(Collectors.joining(" / ")), difference,
                    grades.stream().mapToInt(Grade::getUnverifiedQuotes).sum()));
            System.out.println(rows.getLast());
        }

        String report = """
                # Grader eval

                Model `%s`, reasoning effort `%s`, %d run(s) per answer, %d tokens in total.

                | Answer | Band | Scores | Mean | In band | Section scores | Max section difference | Unverified quotes |
                |---|---|---|---|---|---|---|---|
                %s

                In band: %d of %d. Largest difference between runs on one section: %d.
                """.formatted(properties.model(), properties.reasoningEffort(), RUNS, tokens, String.join("\n", rows),
                inBand, cases.size(), worstDifference);
        Path out = Path.of("target", "grader-eval", "report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);
        System.out.println(report);

        assertThat(inBand).as("answers in band").isGreaterThanOrEqualTo((int) Math.ceil(cases.size() * 10 / 12.0));
        if (RUNS > 1) {
            assertThat(worstDifference).as("largest per-section difference between runs").isLessThanOrEqualTo(1);
        }
    }

    private Grade grade(GradingServiceImpl grader, GradingInput input) {
        for (int attempt = 1; ; attempt++) {
            pace();
            Grade grade = grader.evaluate(input);
            record(grade.getPromptTokens() + grade.getCompletionTokens());
            if (!"RATE_LIMITED".equals(grade.getFailureReason()) || attempt > RATE_LIMIT_RETRIES) {
                return grade;
            }
            sleep(Duration.ofSeconds(60));
        }
    }

    private void pace() {
        int estimate = spent.isEmpty() ? 5_000 : spent.peekLast().tokens();
        while (true) {
            Instant cutoff = Instant.now().minusSeconds(60);
            while (!spent.isEmpty() && spent.peekFirst().at().isBefore(cutoff)) {
                spent.removeFirst();
            }
            int used = spent.stream().mapToInt(Spend::tokens).sum();
            if (spent.isEmpty() || used + estimate <= TOKENS_PER_MINUTE) {
                return;
            }
            sleep(Duration.between(Instant.now(), spent.peekFirst().at().plusSeconds(61)));
        }
    }

    private void record(int tokens) {
        spent.addLast(new Spend(Instant.now(), tokens));
    }

    private static int maxDimensionDifference(List<Grade> grades) {
        int worst = 0;
        for (Grade first : grades) {
            Map<String, Integer> a = scores(first);
            for (Grade second : grades) {
                Map<String, Integer> b = scores(second);
                for (String dimension : a.keySet()) {
                    worst = Math.max(worst, Math.abs(a.get(dimension) - b.getOrDefault(dimension, 0)));
                }
            }
        }
        return worst;
    }

    private static String sectionScores(Grade grade) {
        Map<String, Integer> scores = scores(grade);
        return Arrays.stream(SectionGuide.values())
                .map(guide -> String.valueOf(scores.getOrDefault(guide.key(), 0)))
                .collect(Collectors.joining(""));
    }

    private static Map<String, Integer> scores(Grade grade) {
        return grade.getDimensions().stream()
                .collect(Collectors.toMap(score -> score.getDimension().code(), DimensionScore::getScore));
    }

    private static GradingInput input(EvalCase evalCase, ProblemDefinition problem) {
        List<ChecklistPoint> checklist = new ArrayList<>();
        for (int i = 0; i < problem.keyPoints().size(); i++) {
            var point = problem.keyPoints().get(i);
            checklist.add(new ChecklistPoint(i + 1, point.id(), point.dimension().code(), point.text()));
        }
        Map<String, String> sections = new LinkedHashMap<>();
        Arrays.stream(SectionGuide.values())
                .forEach(guide -> sections.put(guide.key(), evalCase.sections().getOrDefault(guide.key(), "")));
        return new GradingInput(0, 0, problem.title(), problem.prompt().strip(), checklist, sections);
    }

    private static List<EvalCase> cases() throws IOException {
        YAMLMapper yaml = YAMLMapper.builder().build();
        List<EvalCase> cases = new ArrayList<>();
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:grader-eval/*.yaml");
        Arrays.sort(files, (a, b) -> a.getFilename().compareTo(b.getFilename()));
        for (Resource file : files) {
            EvalCase evalCase = yaml.readValue(file.getInputStream(), EvalCase.class);
            if (ONLY.isBlank() || evalCase.name().contains(ONLY)) {
                cases.add(evalCase);
            }
        }
        return cases;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void sleep(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
