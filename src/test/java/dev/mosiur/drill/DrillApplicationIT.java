package dev.mosiur.drill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class DrillApplicationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Clock clock;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void reportsHealthyWhenTheDatabaseIsReachable() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components.db.status").isEqualTo("UP");
    }

    @Test
    void appliesMigrationsOnStartUp() {
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void usesTheConfiguredZoneForTheClock() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Dhaka"));
    }
}
