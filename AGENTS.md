# AGENTS.md

Spring Boot 4.1.1 REST API (Java 17, Maven). Early-stage. Product catalog: Flyway migration + `products` table, JPA entity/repository, `ProductService` (CRUD + DTOs + validation) and REST API `/api/products` (`ProductController` + `@RestControllerAdvice`). Reads are public; writes require HTTP Basic (`ADMIN_USER`/`ADMIN_PASSWORD`, dev defaults `admin`/`admin`).

## Commands
Use the Maven wrapper (Maven 3.10.0), not a system `mvn`.
- Start local DB: `docker compose up -d` (Postgres 17 on `localhost:5433`); stop: `docker compose down`
- Build: `.\mvnw.cmd clean package` (Windows) / `./mvnw clean package`
- Run: `.\mvnw.cmd spring-boot:run`
- All tests: `.\mvnw.cmd test`
- Single test: `.\mvnw.cmd test -Dtest=AutopartsMavenApplicationTests`
- Compile only: `.\mvnw.cmd compile`

## Gotchas
- Base package is `com.autoparts_maven.autoparts_maven` (underscores, not dots) and lives under `src/main/java/com/autoparts_maven/autoparts_maven/`. Match it exactly for new code.
- `application.properties` uses `DB_URL` (default `jdbc:postgresql://localhost:5433/autoparts`), `DB_USERNAME` and `DB_PASSWORD` (both default to `autoparts`, matching `docker-compose.yml`). Integration tests use the `test` profile (`src/test/resources/application-test.properties` + `@ActiveProfiles("test")`), which points to a dedicated `autoparts_test` schema in the same Postgres; Flyway creates it on first run. Run `docker compose up -d` before the tests.
- Host port **5432 is occupied by a native PostgreSQL 18 Windows service** (`postgresql-x64-18`). The Docker Postgres is intentionally published on **5433**; do not move it back to 5432.
- Schema is managed by Flyway through `spring-boot-starter-flyway`. Plain `flyway-core` will NOT run migrations in Spring Boot 4 (autoconfiguration is in the starter). Migrations live in `src/main/resources/db/migration` (naming `V<n>__desc.sql`). JPA runs with `ddl-auto=validate`, so entities must match the migrated schema.
- PostgreSQL is the only runtime datasource. Adding tests that boot the context needs a reachable Postgres or an override.
- Lombok is wired through `maven-compiler-plugin` `annotationProcessorPaths` (both compile and test-compile). Keep that config if editing plugin settings.
- This is Spring Boot 4: web uses `spring-boot-starter-webmvc` and test starters are per-module (`spring-boot-starter-*-test`), not the older `spring-boot-starter-web` / aggregate `spring-boot-starter-test`. In Boot 4 `@AutoConfigureMockMvc` lives in `org.springframework.boot.webmvc.test.autoconfigure`.
- `spring-boot-starter-security` is on the classpath with `config/SecurityConfig` (Spring Security 7 lambda DSL). `GET /api/products/**` is public; writes require HTTP Basic against an in-memory user (`ADMIN_USER`/`ADMIN_PASSWORD`). Controller tests authenticate writes with `spring-security-test`'s `httpBasic(...)`.

## Conventions
- `HELP.md` is gitignored and is stock Spring Initializr boilerplate; ignore it.
- `.gitattributes` pins `mvnw` to LF and `*.cmd` to CRLF; don't reformat those line endings.
- `.env*` files are gitignored. Never commit secrets; rely on env vars.
- Default branch is `main`.
