# RoboLeague

Domain module for a robotics competition platform. The project models tournaments, team eligibility, round scheduling, attempt evaluation, scoring, rankings, tie-breakers, and appeals.

## Project Structure

- `roboleague-domain/`: Java domain model, use cases, repositories, and tests.
- `roboleague-mydomain/`: standalone domain-model entry point.
- [`DESIGN.md`](DESIGN.md): domain contexts, design decisions, applied patterns, and rejected alternatives.

## Requirements

- Java 25
- Maven

## Build and Test

From the repository root:

```bash
mvn test
```

To compile without running tests:

```bash
mvn package -DskipTests
```

## Scope

This delivery focuses on the domain module. It does not include a REST API, frontend, real persistence, authentication, or deployment configuration.
