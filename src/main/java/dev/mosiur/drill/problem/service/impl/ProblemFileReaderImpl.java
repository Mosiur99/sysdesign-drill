package dev.mosiur.drill.problem.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import dev.mosiur.drill.problem.model.KeyPointDefinition;
import dev.mosiur.drill.problem.model.ProblemDefinition;
import dev.mosiur.drill.problem.service.InvalidProblemFileException;
import dev.mosiur.drill.problem.service.ProblemFileReader;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
public class ProblemFileReaderImpl implements ProblemFileReader {

    private static final String EXTENSION = ".yaml";

    private final YAMLMapper mapper = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final ResourcePatternResolver resolver;
    private final Validator validator;
    private final String location;

    public ProblemFileReaderImpl(ResourcePatternResolver resolver, Validator validator,
                                 @Value("${drill.problems.location}") String location) {
        this.resolver = resolver;
        this.validator = validator;
        this.location = location;
    }

    @Override
    public List<ProblemDefinition> readAll() {
        List<Resource> files = findFiles();
        if (files.isEmpty()) {
            throw new InvalidProblemFileException(List.of("no problem files found at " + location));
        }
        List<String> errors = new ArrayList<>();
        List<ProblemDefinition> definitions = new ArrayList<>();
        for (Resource file : files) {
            String name = file.getFilename();
            List<String> fileErrors = new ArrayList<>();
            parse(file, fileErrors).ifPresent(definition -> {
                fileErrors.addAll(validate(definition, name));
                if (fileErrors.isEmpty()) {
                    definitions.add(definition);
                }
            });
            fileErrors.forEach(error -> errors.add(name + ": " + error));
        }
        if (!errors.isEmpty()) {
            throw new InvalidProblemFileException(errors);
        }
        return definitions;
    }

    private List<Resource> findFiles() {
        try {
            return Arrays.stream(resolver.getResources(location))
                    .filter(resource -> resource.getFilename() != null)
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .toList();
        } catch (IOException e) {
            throw new InvalidProblemFileException(List.of("cannot list " + location + ": " + e.getMessage()));
        }
    }

    private Optional<ProblemDefinition> parse(Resource file, List<String> errors) {
        try (InputStream in = file.getInputStream()) {
            return Optional.of(mapper.readValue(in, ProblemDefinition.class));
        } catch (JacksonException e) {
            String line = e.getLocation() == null ? "" : " (line " + e.getLocation().getLineNr() + ")";
            errors.add(e.getOriginalMessage() + line);
        } catch (IOException e) {
            errors.add("cannot read file: " + e.getMessage());
        }
        return Optional.empty();
    }

    private List<String> validate(ProblemDefinition definition, String fileName) {
        List<String> errors = new ArrayList<>(validator.validate(definition).stream()
                .map(violation -> yamlName(violation.getPropertyPath().toString()) + " " + violation.getMessage())
                .sorted()
                .toList());
        String expectedSlug = fileName.substring(0, fileName.length() - EXTENSION.length());
        if (definition.slug() != null && !definition.slug().equals(expectedSlug)) {
            errors.add("slug '" + definition.slug() + "' must match the file name '" + expectedSlug + "'");
        }
        if (definition.keyPoints() != null) {
            duplicateIds(definition.keyPoints())
                    .forEach(id -> errors.add("key point id '" + id + "' is used more than once"));
        }
        return errors;
    }

    private static String yamlName(String propertyPath) {
        return propertyPath.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private Set<String> duplicateIds(List<KeyPointDefinition> keyPoints) {
        return keyPoints.stream()
                .filter(keyPoint -> keyPoint != null && keyPoint.id() != null)
                .collect(Collectors.groupingBy(KeyPointDefinition::id, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
