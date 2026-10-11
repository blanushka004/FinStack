# FinStack

FinStack is a full-stack, ownership-scoped personal-finance portfolio application. The Spring Boot API provides JWT authentication, accounts and atomic transaction balances, monthly budgets, alerts, dashboard analytics, and **rule-based** fraud assessment. The React/Vite client provides the responsive workspace. This is not a banking system or a trained ML product.

## What is implemented

| Layer | Status | Limitation |
| --- | --- | --- |
| Backend | Java 21 target, Spring Boot 3.5.16, Security/JWT, JPA, PostgreSQL, Flyway V1–V9, OpenAPI | Verify Flyway and the full API flow against each deployment database. |
| Finance | Accounts, categories, completed/pending/cancelled transactions, balance reversal, pessimistic account locks, monthly budgets, in-app threshold alerts | No transfers or recurring-transaction scheduler. |
| Risk | Persisted rule-based assessments, owner-scoped API, high-risk alerts | Kafka delivery is best-effort, not a durable outbox. No trained ML. |
| Client | React, TypeScript, Vite, Tailwind, TanStack Query, forms, charts, alerts, fraud and admin views | Browser end-to-end QA is separate from automated builds. |
| Optional services | Kafka integration and native fallback; Redis dependency/configuration; Docker Compose | No Redis-backed cache or Redis-specific product feature is active. |

Identity comes from the JWT, never a client-supplied user ID. Finance resources and fraud queries are owner-scoped. Completed income adds to balance; completed expense subtracts. Pending/cancelled transactions have no balance effect. Updating reverses the old completed effect and applies the new one atomically; cancellation reverses once. Deposit accounts reject insufficient funds; credit cards can use an optional credit limit.

Budget totals include **all** completed expenses in the month, even unallocated categories. An allocation counts only the matching category ID. Dashboard cash flow and category charts use completed transactions; budget bars use the budget progress API; fraud distribution uses persisted assessments. No chart uses fabricated data.

## Native Windows setup (Docker not required)

Install a Java 21+ JDK (Maven compiles with `--release 21`), Node.js/npm, and PostgreSQL. Create an empty database `finstack_db` with a user permitted to create tables. Flyway applies migrations V1–V8 on startup; do not create tables manually or reset an existing database. Kafka and Redis are optional.

In PowerShell at `D:\FinStack`, set these in the **same session** that starts the backend. `.env.example` is a reference, not a file Spring automatically loads:

```powershell
$env:SERVER_PORT = '8081'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/finstack_db'
$env:DB_USERNAME = 'postgres'
$password = Read-Host 'PostgreSQL password' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
try { $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
$jwtBytes = New-Object byte[] 48
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($jwtBytes) } finally { $rng.Dispose() }
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$env:KAFKA_ENABLED = 'false'
$env:CACHE_TYPE = 'simple'
$env:REDIS_HEALTH_ENABLED = 'false'
Set-Location .\backend
.\mvnw.cmd spring-boot:run --no-transfer-progress
```

Use a stable, securely stored `JWT_SECRET` beyond a disposable local session: generating a new one invalidates existing JWTs. Never commit or paste real DB/JWT/SMTP credentials. Without SMTP, email delivery is unavailable. On a trusted local-only machine, set `$env:SPRING_PROFILES_ACTIVE='dev'`, `$env:SERVER_ADDRESS='127.0.0.1'`, and `$env:PASSWORD_RESET_DEV_LOG_LINK='true'` before starting the backend to print **only the reset link** in the backend console; `FRONTEND_BASE_URL` must point to `localhost` or `127.0.0.1`. Never enable this on a shared host or in production. The existing verification-link dev flag is independent. Under the current demo policy, unverified users can log in, but `emailVerified` remains false until verification.

In another PowerShell window:

```powershell
Set-Location D:\FinStack\frontend
npm.cmd install
npm.cmd run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to `http://localhost:8081`. Health: `http://localhost:8081/actuator/health`. Development Swagger UI: `http://localhost:8081/swagger-ui/index.html`; OpenAPI JSON: `http://localhost:8081/v3/api-docs`. Disable Swagger in production with `SPRINGDOC_API_DOCS_ENABLED=false` and `SPRINGDOC_SWAGGER_UI_ENABLED=false`. A separately hosted frontend needs an explicitly allowed CORS origin, not wildcard credentialed CORS.

## Configuration and optional Docker

See [`.env.example`](.env.example) for names and safe, nonworking placeholders. Required for native startup: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and a strong `JWT_SECRET` (32+ bytes). Optional settings: `JWT_ACCESS_TOKEN_EXPIRATION_MS`, `SERVER_PORT`, `MAIL_*`, `FRONTEND_BASE_URL`, `EMAIL_VERIFICATION_DEV_LOG_LINK`, `PASSWORD_RESET_EXPIRATION_MINUTES`, `PASSWORD_RESET_DEV_LOG_LINK`, `KAFKA_ENABLED`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TRANSACTION_TOPIC`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `CACHE_TYPE`, and `REDIS_HEALTH_ENABLED`. The frontend's optional `VITE_API_BASE_URL` belongs in `frontend/.env`; it is a public URL, never a secret. Supply production secrets externally. The default `CACHE_TYPE=simple` does not mean finance reads are cached; no cache annotations are active.

Compose defines PostgreSQL, Redis, single-node Kafka 4.0 KRaft, and the backend. Kafka advertises `localhost:9092` to native clients and `kafka:29092` to Compose clients. Docker/WSL2 virtualization must actually work on the host. Inspect configuration without a running daemon using `docker compose config --quiet`. Set secure `JWT_SECRET` and `DB_PASSWORD` before starting containers; the example provides neither. The backend depends only on PostgreSQL and defaults to `KAFKA_ENABLED=false`, so optional Kafka/Redis outages do not block ordinary finance APIs. For a minimal Compose run use `docker compose up -d --build postgres backend`; start Kafka separately only to test event delivery. Compose is development infrastructure, not a production deployment recipe. Native setup above remains fully supported.

### Production prerequisites

Use externally managed DB, JWT, and SMTP secrets; HTTPS; an explicit frontend CORS origin; and a real SMTP provider for password reset and email verification. Keep `PASSWORD_RESET_DEV_LOG_LINK=false`, `EMAIL_VERIFICATION_DEV_LOG_LINK=false`, `SPRINGDOC_API_DOCS_ENABLED=false`, and `SPRINGDOC_SWAGGER_UI_ENABLED=false` in production. Review database backups, Flyway rollout, alert/audit retention, monitoring, and browser QA before deployment. Kafka delivery is best-effort without an outbox, Redis caching is not implemented, and no trained ML model exists. A successful build or local Compose run alone is not deployment validation.

## Forgot-password flow

The login screen links to `/forgot-password`. `POST /api/v1/auth/password-reset/request` accepts `{"email":"person@example.com"}` and returns the **same** 200 message for known and unknown addresses (and throttled requests). A known address gets a 32-byte random, one-use link to `${FRONTEND_BASE_URL}/reset-password?token=...`; only SHA-256 of the token is stored. Each new request invalidates older outstanding links. `POST /api/v1/auth/password-reset/confirm` accepts `{"token":"...","password":"NewStrongPass1!"}` and returns 204 on success, 400 `INVALID_RESET_TOKEN` for an invalid/used link, or 410 `RESET_TOKEN_EXPIRED` for an expired link. Passwords follow the registration policy (8–72 characters with uppercase, lowercase, digit, and special character) and are BCrypt-hashed. Reset increments the user's JWT token version, so existing access tokens for that user are rejected; other users' tokens remain valid. The browser clears its local session on successful reset.

Requests are throttled to five per normalized email and 30 per remote IP per 15 minutes per app instance. This is a best-effort local throttle; a multi-instance deployment also needs a shared edge/gateway rate limit. Neither response contains a token or reveals account existence. Reset links are only sent by configured SMTP, except when `SPRING_PROFILES_ACTIVE=dev`, `PASSWORD_RESET_DEV_LOG_LINK=true`, a loopback `FRONTEND_BASE_URL`, and loopback `SERVER_ADDRESS` are all explicitly set. This local console link can be used for testing without SMTP; it must never be enabled in production. Configure `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH`, and `MAIL_SMTP_STARTTLS` for real mail. Do not claim delivery until tested with your SMTP provider.

For a local check, restart the backend with the private DB/JWT variables and optional dev-link settings, confirm Flyway reports V9, and request a link from `/forgot-password` for a clearly identified QA account. In no-SMTP dev mode, look for the `DEV ONLY password reset link` line in the **Spring Boot backend PowerShell/IDE Run console**, not the Vite terminal, browser console, or public API response. The link is issued only for an existing address; the response is intentionally identical for unknown addresses. Open only the newest link within 30 minutes—each new request invalidates prior links, and more than five requests per email in 15 minutes are silently throttled. Set a strong replacement password only for that QA account, then verify old-password login fails, new-password login succeeds, and the prior JWT receives 401 on `/api/v1/users/me`. Reopening the same link must fail. Test a nonexistent email and confirm its public response is identical. Avoid sharing the console link or browser history.

## API and automated checks

Public auth endpoints are under `/api/v1/auth`. Protected APIs include `/api/v1/users/me`, `/api/v1/accounts`, `/api/v1/categories`, `/api/v1/transactions`, `/api/v1/budgets`, `/api/v1/alerts`, `/api/v1/dashboard/summary`, `/api/v1/dashboard/analytics`, `/api/v1/fraud/assessments`, and `/api/v1/fraud/summary`. `/api/v1/admin/login-audit` requires `ROLE_ADMIN`. Swagger documents the DTOs and bearer scheme.

```powershell
Set-Location D:\FinStack\backend
.\mvnw.cmd clean verify --no-transfer-progress
Set-Location D:\FinStack\frontend
npm.cmd run build
npm.cmd run lint
```

The test suite covers authentication/ownership, balances, budget rules, fraud mapping, and dashboard SQL aggregation on in-memory H2. That does not replace PostgreSQL runtime verification.

### Retrying transaction creation safely

`POST /api/v1/transactions` accepts an optional `Idempotency-Key` header (1–128 ASCII letters, digits, `.`, `_`, `:`, or `-`). Generate a new random key for each **new** intended transaction, such as a UUID. If a response is lost or times out, retry the **same request body with the same key**. The API replays the original 201 response without another transaction row or balance change. Reusing a key for a different body returns 409 `IDEMPOTENCY_KEY_CONFLICT`; an invalid key returns 400. Keys are scoped to the authenticated user and stored durably by Flyway V8, in the same database transaction as the finance write. Requests without the header keep the previous non-idempotent behavior. The frontend uses a stable key across uncertain retries and resets it after success or a definitive 4xx response. Avoid retaining sensitive data in the key itself.

## Manual QA checklist with local PostgreSQL

Use Swagger or the frontend with two newly registered users and keep credentials private.

With the native backend already running, `scripts/live-qa.ps1` exercises the two-user API flow, idempotent transaction retries, budget/fraud alerts, ownership checks, analytics, and cancellation. It generates credentials in memory and does not print them or delete data. Each run **creates and retains** uniquely named QA users and finance records, so run it only against a local development database:

```powershell
Set-Location D:\FinStack
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\live-qa.ps1
```

The per-process execution-policy option does not change the machine's persistent policy. A successful run ends with `LIVE_QA_COMPLETE`. An automated build does not replace this PostgreSQL check or browser inspection.

1. Register, log in, call `/api/v1/users/me`, and optionally follow the verification link. Bad credentials/malformed requests should give safe 4xx responses; anonymous protected requests should give 401.
2. Create a savings account with an opening balance. Add completed income and expense, refresh accounts, and verify exact balance. An expense exceeding funds must fail without a row or balance mutation.
3. Create a pending transaction: no balance effect. Complete/edit it, then cancel twice: balance reverses exactly once. Analytics must exclude pending/cancelled transactions.
4. Create an expense category and monthly budget allocation with that category ID. Add completed allocated and unallocated expenses; check overall/category progress and warning/exceeded alerts. Income and other months must not count.
5. Compare `/api/v1/dashboard/analytics` chart figures to stored completed transactions. Refresh after mutations, and check the empty-user state.
6. With `KAFKA_ENABLED=false`, create a completed transaction, then inspect `/api/v1/fraud/assessments` and `/api/v1/fraud/summary`. A high rule-based score should produce one `FRAUD_SUSPECTED` alert. Bell, mark-read, and dashboard unread count should agree. Kafka-enabled processing needs separate broker QA.
7. With user A's JWT, try user B's account, transaction, budget, custom category and fraud IDs: no B data should be returned. A normal user should get 403 from admin login audit.
8. Inspect desktop/mobile views, validation/retry states, email delivery mode, and browser network/console. These are manual checks, not claimed by Maven or Vite.

The fraud rules score large amount, odd-hour timing, and high account utilization. Scores 0–29 are low, 30–59 medium, 60–100 high. Delayed events for a transaction already cancelled are skipped. Assessment history may still include assessments created *before* cancellation. After-commit Kafka publishing intentionally cannot roll back committed financial changes. If disabled or publishing fails, the local fraud service attempts assessment, but there is no guaranteed replay/outbox. Redis configuration exists but no Redis-backed feature is active. Add portfolio screenshots only after real browser QA.
