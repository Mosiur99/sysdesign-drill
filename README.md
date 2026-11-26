# System Design Drill

A daily system design practice app. Every morning it picks one problem, you write a structured answer against a 45-minute timer, and an LLM grades it against a fixed rubric and a hidden checklist of key points. Weak topics and low scores come back as spaced retries.

Work in progress. Built step by step.

## Stack

Java 25, Spring Boot 4, PostgreSQL 17, Flyway, Testcontainers.

## Run locally

```bash
docker compose up -d
./mvnw spring-boot:run
curl localhost:8080/actuator/health
```

## Test

```bash
./mvnw verify
```

Unit tests run with Surefire. Integration tests (`*IT`) run with Failsafe against a real PostgreSQL container, so Docker must be running.
