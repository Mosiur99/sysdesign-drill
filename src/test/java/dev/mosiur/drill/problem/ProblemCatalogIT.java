package dev.mosiur.drill.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.mosiur.drill.PostgresTestConfiguration;
import dev.mosiur.drill.problem.domain.Dimension;
import dev.mosiur.drill.problem.domain.KeyPoint;
import dev.mosiur.drill.problem.domain.Problem;
import dev.mosiur.drill.problem.domain.Topic;
import dev.mosiur.drill.problem.model.CatalogSyncResult;
import dev.mosiur.drill.problem.model.KeyPointDefinition;
import dev.mosiur.drill.problem.model.ProblemDefinition;
import dev.mosiur.drill.problem.repository.ProblemRepository;
import dev.mosiur.drill.problem.service.ProblemCatalogService;
import dev.mosiur.drill.problem.service.ProblemFileReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class ProblemCatalogIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ProblemFileReader reader;

    @Autowired
    private ProblemCatalogService catalog;

    @Autowired
    private ProblemRepository problems;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void loadsTheShippedBankOnStartUp() {
        int shipped = reader.readAll().size();

        assertThat(problems.findByActiveTrue()).hasSize(shipped);
    }

    @Test
    void listsProblemsByDifficultyThenTitleWithoutKeyPoints() {
        client.get().uri("/api/problems")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].difficulty").isEqualTo(1)
                .jsonPath("$[0].title").isEqualTo("Design a distributed rate limiter")
                .jsonPath("$[0].topics[0]").isEqualTo("caching")
                .jsonPath("$[0].keyPoints").doesNotExist()
                .jsonPath("$[0].prompt").doesNotExist();
    }

    @Test
    @Transactional
    void resyncIsIdempotentAndAppliesEdits() {
        List<ProblemDefinition> shipped = reader.readAll();
        assertThat(catalog.sync(shipped)).isEqualTo(new CatalogSyncResult(0, shipped.size(), 0));

        List<ProblemDefinition> edited = new ArrayList<>(shipped.subList(1, shipped.size()));
        ProblemDefinition first = edited.removeFirst();
        edited.add(new ProblemDefinition(first.slug(), "Renamed", first.difficulty(), Set.of(Topic.GEO),
                first.prompt(), List.of(
                        new KeyPointDefinition("only-point", Dimension.API, "Only point"),
                        first.keyPoints().get(1), first.keyPoints().get(2), first.keyPoints().get(3))));

        CatalogSyncResult result = catalog.sync(edited);

        assertThat(result).isEqualTo(new CatalogSyncResult(0, shipped.size() - 1, 1));
        Problem removed = problems.findAll().stream()
                .filter(problem -> problem.getSlug().equals(shipped.getFirst().slug()))
                .findFirst().orElseThrow();
        assertThat(removed.isActive()).isFalse();
        Problem renamed = problems.findAll().stream()
                .filter(problem -> problem.getSlug().equals(first.slug()))
                .findFirst().orElseThrow();
        assertThat(renamed.getTitle()).isEqualTo("Renamed");
        assertThat(renamed.getTopics()).containsExactly(Topic.GEO);
        assertThat(renamed.activeKeyPoints()).extracting(KeyPoint::getCode)
                .hasSize(4).contains("only-point").doesNotContain(first.keyPoints().getFirst().id());
    }
}
