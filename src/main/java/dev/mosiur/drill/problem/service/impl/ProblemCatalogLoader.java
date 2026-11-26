package dev.mosiur.drill.problem.service.impl;

import dev.mosiur.drill.problem.model.CatalogSyncResult;
import dev.mosiur.drill.problem.service.ProblemCatalogService;
import dev.mosiur.drill.problem.service.ProblemFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProblemCatalogLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProblemCatalogLoader.class);

    private final ProblemFileReader reader;
    private final ProblemCatalogService catalog;

    public ProblemCatalogLoader(ProblemFileReader reader, ProblemCatalogService catalog) {
        this.reader = reader;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments args) {
        CatalogSyncResult result = catalog.sync(reader.readAll());
        log.info("Problem bank synced: {} created, {} updated, {} deactivated",
                result.created(), result.updated(), result.deactivated());
    }
}
