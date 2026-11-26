package dev.mosiur.drill.problem.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import dev.mosiur.drill.problem.domain.Dimension;
import dev.mosiur.drill.problem.domain.Topic;
import dev.mosiur.drill.problem.model.KeyPointDefinition;
import dev.mosiur.drill.problem.model.ProblemDefinition;
import dev.mosiur.drill.problem.service.InvalidProblemFileException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class ProblemFileReaderImplTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void readsEveryFileInNameOrder() {
        List<ProblemDefinition> definitions = reader("classpath:problem-files/valid/*.yaml").readAll();

        assertThat(definitions).extracting(ProblemDefinition::slug).containsExactly("cache-design", "queue-design");
        ProblemDefinition cache = definitions.getFirst();
        assertThat(cache.title()).isEqualTo("Design a cache");
        assertThat(cache.difficulty()).isEqualTo(2);
        assertThat(cache.topics()).isEqualTo(Set.of(Topic.CACHING, Topic.REAL_TIME));
        assertThat(cache.prompt()).isEqualTo("Design a cache.\n");
        assertThat(cache.keyPoints()).first()
                .isEqualTo(new KeyPointDefinition("eviction", Dimension.DEEP_DIVE, "Picks an eviction policy"));
    }

    @Test
    void reportsEveryInvalidFileWithItsName() {
        ProblemFileReaderImpl reader = reader("classpath:problem-files/invalid/*.yaml");

        assertThatThrownBy(reader::readAll)
                .isInstanceOfSatisfying(InvalidProblemFileException.class, e -> assertThat(e.errors())
                        .hasSize(7)
                        .anySatisfy(error -> assertThat(error)
                                .startsWith("bad-difficulty.yaml: difficulty").contains("less than or equal to 3"))
                        .anySatisfy(error -> assertThat(error)
                                .startsWith("unknown-topic.yaml:").contains("unknown topic 'blockchain'").endsWith("(line 4)"))
                        .anySatisfy(error -> assertThat(error)
                                .startsWith("unknown-dimension.yaml:").contains("unknown dimension 'vibes'"))
                        .anySatisfy(error -> assertThat(error)
                                .startsWith("unknown-field.yaml:").contains("titel"))
                        .anySatisfy(error -> assertThat(error).isEqualTo(
                                "wrong-name.yaml: slug 'other-name' must match the file name 'wrong-name'"))
                        .anySatisfy(error -> assertThat(error).isEqualTo(
                                "duplicate-ids.yaml: key point id 'a' is used more than once"))
                        .anySatisfy(error -> assertThat(error)
                                .startsWith("too-few-points.yaml: key_points").contains("between 4 and 10")));
    }

    @Test
    void failsWhenNoFilesExist() {
        ProblemFileReaderImpl reader = reader("classpath:problem-files/empty/*.yaml");

        assertThatThrownBy(reader::readAll)
                .isInstanceOf(InvalidProblemFileException.class)
                .hasMessageContaining("no problem files found at classpath:problem-files/empty/*.yaml");
    }

    @Test
    void shippedProblemBankIsValid() {
        List<ProblemDefinition> definitions = reader("classpath:problems/*.yaml").readAll();

        assertThat(definitions).hasSizeGreaterThanOrEqualTo(10);
    }

    private static ProblemFileReaderImpl reader(String location) {
        return new ProblemFileReaderImpl(new PathMatchingResourcePatternResolver(), validator, location);
    }
}
