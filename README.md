# System Design Drill

A daily system design practice app. Every morning it picks one problem, you write a structured answer against a 45-minute timer, and an LLM grades it against a fixed rubric and a hidden checklist of key points. Weak topics and low scores come back as spaced retries.

Work in progress. Built step by step.

## Stack

Java 25, Spring Boot 4, PostgreSQL 17, Flyway, Spring Security, Thymeleaf, htmx, Mermaid, Testcontainers, Playwright.

## Run locally

The app has one user. Set the username and a BCrypt hash of the password:

```bash
export DRILL_USER=you
export DRILL_PASSWORD_HASH="$(htpasswd -bnBC 12 '' 'your-password' | tr -d ':\n')"
docker compose up -d
./mvnw spring-boot:run
```

Grading uses Groq (`openai/gpt-oss-120b`). Put the key in `.env` (git-ignored) as `GROQ_API_KEY=...`. Without it the app runs, but grading reports that the key is missing.

Open http://localhost:8080 and sign in. Set `DRILL_SECURE_COOKIE=true` when the app is served over HTTPS.

## Test

```bash
./mvnw verify
```

Unit tests run with Surefire. Integration tests (`*IT`) run with Failsafe against a real PostgreSQL container, so Docker must be running. `EditorUiIT` drives the editor in headless Chromium with Playwright, which downloads the browser on its first run. `LlmClientImplTest` runs the Groq client against WireMock. `LlmLiveSmokeIT` makes one real call and runs only when `GROQ_API_KEY` is set:

```bash
set -a; source .env; set +a
./mvnw verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=LlmLiveSmokeIT
```
