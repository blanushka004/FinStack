# FinStack backend

See the [root setup and QA guide](../README.md) for Windows PowerShell commands, PostgreSQL requirements, environment variables, Docker's optional role, and manual flow checks.

The API targets Java 21 with Spring Boot 3.5.16. Flyway V1–V8 owns the PostgreSQL schema; JPA validates it. V8 adds per-user transaction-create idempotency records. Native development does not need Kafka or Redis. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and a strong `JWT_SECRET` in the PowerShell session, then run:

```powershell
.\mvnw.cmd spring-boot:run --no-transfer-progress
.\mvnw.cmd clean verify --no-transfer-progress
```

The default port is 8081. Development Swagger UI is at `http://localhost:8081/swagger-ui/index.html`. Kafka publishing is optional and after-commit; rule-based fraud assessment has a local fallback, but no durable outbox. Redis repositories are disabled and there is currently no Redis-backed caching feature. Compose also includes Kafka; it is not required for native finance operations.
