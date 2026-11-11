[README.md](https://github.com/user-attachments/files/34698548/README.md)
# Digital Wallet Backend

A Spring Boot–based digital wallet backend that supports wallet creation, balance retrieval, secure transfers, transaction history, idempotent transfer requests, and a double-entry transaction ledger. PostgreSQL is the system of record, while Redis is used as an optional cache for wallet balances.

> **Interview-preparation note:** This README is based on the project details and debugging notes available in the supplied conversation. Confirm the package names, endpoint paths, environment-variable names, and configuration values against the final code before publishing. Do not claim that a feature is production-ready merely because it is described here.

---

## Table of contents

1. [Project overview](#1-project-overview)
2. [Problem statement](#2-problem-statement)
3. [Goals and scope](#3-goals-and-scope)
4. [Technology stack](#4-technology-stack)
5. [Architecture](#5-architecture)
6. [Core concepts and implementation](#6-core-concepts-and-implementation)
7. [Transfer workflow](#7-transfer-workflow)
8. [API overview](#8-api-overview)
9. [Database design](#9-database-design)
10. [Security and reliability](#10-security-and-reliability)
11. [Testing strategy](#11-testing-strategy)
12. [Debugging and troubleshooting story](#12-debugging-and-troubleshooting-story)
13. [Known limitations](#13-known-limitations)
14. [Future enhancements](#14-future-enhancements)
15. [Interview preparation](#15-interview-preparation)
16. [Run the project locally](#16-run-the-project-locally)
17. [Reproduce the project on another system](#17-reproduce-the-project-on-another-system)
18. [Useful commands](#18-useful-commands)

---

## 1. Project overview

The Digital Wallet Backend is a RESTful application for managing user wallets and transferring funds between wallets.

The project focuses on backend engineering concerns that are important in financial applications:

- Maintaining correct wallet balances.
- Recording every successful transfer in a ledger.
- Preventing duplicate processing when a client retries a request.
- Protecting concurrent transfers from race conditions.
- Keeping database changes atomic.
- Using Redis to reduce repeated balance reads without making Redis a dependency for financial correctness.
- Returning consistent API errors and making unexpected failures diagnosable.

### Main capabilities

- Create wallets for users.
- Retrieve wallet balances.
- Transfer funds between two wallets.
- Store debit and credit ledger entries for a transfer.
- Return the original result when a transfer is retried with the same idempotency key.
- Retrieve paginated transaction history.
- Authenticate API requests using JWT-based authentication.
- Cache wallet balances in Redis, with PostgreSQL as the source of truth.

---

## 2. Problem statement

A basic wallet application that only subtracts money from one balance and adds it to another is not sufficient for reliable financial operations.

Consider a transfer of ₹100 from Wallet A to Wallet B. The system must ensure that:

1. Wallet A is debited by exactly ₹100.
2. Wallet B is credited by exactly ₹100.
3. Both balance changes succeed together, or neither succeeds.
4. The transaction is recorded for both wallets.
5. A retry of the same request does not transfer the money again.
6. Two simultaneous transfers cannot spend the same available balance.
7. A cache outage does not cause a valid database transfer to fail.

The project addresses these problems through database transactions, row-level locking, a double-entry ledger, idempotency keys, and a failure-tolerant Redis cache.

---

## 3. Goals and scope

### Goals

- Implement wallet operations through REST APIs.
- Preserve balance consistency during transfers.
- Support safe retries using idempotency keys.
- Prevent common concurrent-update problems.
- Keep an auditable record of debit and credit entries.
- Support paginated transaction history.
- Separate API, service, repository, entity, DTO, and exception-handling responsibilities.
- Run the application and its dependencies using Docker Compose.

### Scope

This is an educational/backend portfolio project. It demonstrates important transaction-processing patterns, but it is not a real banking or payment system. See [Known limitations](#13-known-limitations) before presenting it as production software.

---

## 4. Technology stack

| Technology | Role in the project | Why it is used |
|---|---|---|
| Java 21 | Main programming language | Strong typing, mature ecosystem, object-oriented design, and broad backend support. |
| Spring Boot | Application framework | Provides dependency injection, REST API support, configuration, and integration with persistence and security libraries. |
| Spring Web | HTTP/API layer | Exposes wallet operations as REST endpoints. |
| Spring Data JPA / Hibernate | Persistence layer | Maps Java entities to relational tables and reduces repetitive database-access code. Verify the exact dependencies in `pom.xml`. |
| Spring Data Redis | Cache integration | Provides Redis access through Spring abstractions such as `StringRedisTemplate`. |
| PostgreSQL | Primary database | Stores users, wallets, transaction records, and idempotency records with relational integrity and transaction support. |
| Redis | Balance cache | Speeds up repeated balance reads. It is not the source of truth for money movement. |
| Spring Security | API security | Protects endpoints and integrates authentication/authorization. |
| JWT | Stateless authentication | Allows clients to send an access token with subsequent API requests. |
| Maven | Build and dependency management | Resolves dependencies and builds the executable JAR. |
| Docker | Containerization | Packages the application and provides repeatable runtime environments. |
| Docker Compose | Local orchestration | Starts the application, PostgreSQL, and Redis together. |
| JUnit / Mockito | Automated testing | Tests business logic and service behavior. Confirm the exact test dependencies in `pom.xml`. |
| Shell (`e2e_test.sh`) | End-to-end verification | Exercises the running API and checks expected responses and database/cache effects. |

### Why Java and Spring Boot?

Java provides a structured object-oriented language for modeling users, wallets, and transactions. Spring Boot reduces infrastructure code and makes it practical to build REST services with dependency injection, validation, persistence, and security.

### Why PostgreSQL?

Wallet balances and ledger records require durable, consistent storage. PostgreSQL supports ACID transactions, row-level locks, constraints, and precise numeric types. These properties are more important for financial records than relying on an in-memory cache.

### Why Redis?

Balance reads can be frequent. Redis can return a recently cached balance quickly and reduce repeated database reads. However, Redis must remain an optimization: a Redis outage should cause a database fallback, not a failed transfer.

### Why Docker Compose?

The application depends on more than one service. Compose provides a repeatable way to start the backend, database, and cache with service-name-based networking and health checks.

---

## 5. Architecture

The project follows a layered backend architecture.

```text
Client (Postman / curl / frontend)
              |
              v
        REST Controller
              |
              v
        Service Layer
       /      |       \
      v       v        v
 PostgreSQL  Redis   Security /
 (JPA)      (cache)  Validation
      |
      v
 Entities and repositories

Cross-cutting concerns:
- JWT authentication
- Global exception handling
- Transaction management
- Logging
```

### Layer responsibilities

| Layer | Responsibility |
|---|---|
| Controller | Receives HTTP requests, validates input, calls services, and returns HTTP responses. |
| DTO | Defines request and response shapes so persistence entities are not exposed directly. |
| Service | Implements wallet business rules and coordinates database/cache operations. |
| Repository | Performs database operations through Spring Data JPA or custom queries. |
| Entity | Represents persistent domain data such as wallets and transactions. |
| Security | Authenticates requests and protects restricted endpoints. |
| Exception handler | Converts application exceptions into consistent HTTP error responses. |

### Suggested package structure

The supplied code uses the base package `com.stark.wallet.wallet_app`. The structure below is a guide; retain the actual names and packages in your repository.

```text
wallet-app/
├── src/
│   ├── main/
│   │   ├── java/com/stark/wallet/wallet_app/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── entity/
│   │   │   ├── exception/
│   │   │   ├── repository/
│   │   │   ├── security/
│   │   │   ├── service/
│   │   │   └── WalletAppApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── schema.sql
│   │       └── ...
│   └── test/
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── .dockerignore
├── e2e_test.sh
├── pom.xml
└── README.md
```

---

## 6. Core concepts and implementation

### 6.1 REST API

REST exposes resources and operations over HTTP. Controllers map HTTP requests to service methods. The service layer owns business rules; controllers should not contain transfer or balance-update logic.

Interview explanation:

> “I use REST controllers as the entry point. They validate and translate HTTP requests into service calls. The service layer performs the wallet business logic, and repositories handle persistence. This separation keeps the code easier to test and maintain.”

### 6.2 Dependency injection

Spring creates and injects the objects required by a class. For example, `WalletService` receives wallet, transaction, and idempotency repositories, along with `StringRedisTemplate`, through its constructor.

Why constructor injection?

- Dependencies are explicit.
- Fields can be `final`.
- Unit tests can provide mocks.
- Missing dependencies are detected when Spring creates the bean.

A bug encountered during development was a constructor that accepted `IdempotencyKeyRepository` but did not assign it to the service field. The transfer method then failed when it tried to perform the idempotency lookup. Constructor assignments should be checked whenever a service dependency is added.

### 6.3 ACID transactions and `@Transactional`

A transfer updates two wallet balances and inserts two ledger rows plus an idempotency record. These database changes must behave as one unit.

`@Transactional` establishes a database transaction around the service method. If a runtime failure occurs before commit, the database transaction is rolled back.

Interview explanation:

> “A transfer has multiple related database writes. I put the operation inside a transaction so a partial transfer cannot be committed. Either the debit, credit, ledger entries, and idempotency record commit together, or the database rolls them back.”

Important distinction: Redis is not automatically part of the PostgreSQL transaction. That is why cache eviction is scheduled after the database commit and Redis exceptions are caught.

### 6.4 Double-entry ledger

For a transfer of ₹100 from Wallet A to Wallet B, the application records two entries with the same transaction group ID:

| Wallet | Direction | Amount |
|---|---|---:|
| Wallet A | DEBIT | 100 |
| Wallet B | CREDIT | 100 |

The entries share a `transactionGroupId`, which links them to the same logical transfer.

Why this matters:

- The debit and credit can be inspected together.
- Transaction history can show each wallet's side of the transfer.
- The ledger provides an audit trail beyond the current balance.
- A successful transfer should have matching debit and credit entries.

The balance is the current state; the ledger is the record of how that state changed.

### 6.5 Idempotency

An HTTP client may retry a request because of a timeout even when the server completed the original transfer. Without idempotency, the retry could debit the sender twice.

The client supplies an idempotency key. The service checks whether that key already exists:

1. If the key exists, return the stored transfer result.
2. If it does not exist, process the transfer.
3. Save the key and the result in the same database transaction.

The idempotency record stores the transaction group ID, sender's new balance, status, and creation time.

Interview explanation:

> “Idempotency makes retries safe. A repeated request with the same key returns the original result instead of executing another money movement.”

**Important implementation caveat:** A lookup followed by an insert can still race when two requests with the same new key arrive simultaneously. Enforce a unique database constraint on the idempotency key and handle duplicate-key conflicts safely. Also validate that a key is not reused for a different request payload; request fingerprinting is a useful enhancement.

### 6.6 Pessimistic locking and concurrency

Two requests may try to spend from the same wallet at the same time. If both read the same old balance before either update commits, both might incorrectly pass the balance check.

The project uses a repository method named `findByIdForUpdate(...)` to retrieve wallet rows under a database write lock (verify that its query uses `SELECT ... FOR UPDATE`).

The service locks both wallet rows in a deterministic order based on UUID comparison. This reduces the chance of deadlocks when two transfers involve the same pair of wallets in opposite directions.

Interview explanation:

> “I use pessimistic row locking because a transfer must make its balance check and update against a protected row. I also acquire the two wallet locks in a consistent order to reduce deadlock risk.”

### 6.7 Redis cache-aside pattern

The balance read follows a cache-aside approach:

1. Build a key such as `wallet:balance:<walletId>`.
2. Try to read the value from Redis.
3. If present, parse and return it.
4. If absent or Redis fails, read the wallet from PostgreSQL.
5. Attempt to cache the database value with a five-minute TTL.
6. Return the database value.

The transfer schedules cache eviction after the PostgreSQL transaction commits. If Redis is unavailable, the eviction failure is logged and the cache TTL eventually expires.

Interview explanation:

> “PostgreSQL remains the source of truth. Redis only accelerates reads. I made cache reads, writes, and eviction best-effort so Redis downtime does not block the financial operation.”

**Consistency caveat:** Post-commit eviction and TTL reduce stale-cache risk, but they do not provide strict cache consistency in every race or failure scenario. A production design may use versioned cache values, an outbox/event mechanism, or another coordinated invalidation strategy.

### 6.8 Pagination

Transaction history is retrieved in pages rather than loading every transaction at once. A `Pageable` request controls page number and page size. The response includes the records and metadata such as current page, total pages, total elements, and whether another page exists.

Benefits:

- Limits response size.
- Reduces memory and database work for large histories.
- Gives clients predictable navigation through records.

Validate page and size values to prevent negative pages or unreasonably large requests.

### 6.9 DTOs and validation

DTOs define the API contract independently of database entities. Request validation should reject invalid values before business logic executes.

For transfers, the service also checks that:

- The amount is present and positive.
- Sender and receiver are different wallets.
- The sender has enough balance.

The service-level checks are important even if request DTO validation exists, because business rules should not depend exclusively on a particular controller.

### 6.10 Exception handling

A global exception handler maps known failures to meaningful HTTP responses. Examples from the project notes:

| Condition | Expected HTTP status |
|---|---:|
| Invalid request / non-positive amount | 400 Bad Request |
| Wallet not found | 404 Not Found |
| Insufficient balance | 409 Conflict |
| Invalid credentials | 401 Unauthorized |
| Unexpected server error | 500 Internal Server Error |

Unexpected exceptions should be logged with the request URI and stack trace, while the API response should avoid leaking internal details.

---

## 7. Transfer workflow

```text
Client sends transfer request + idempotency key
                    |
                    v
          Validate amount and wallets
                    |
                    v
          Look up idempotency key
             /             \
          Found           Not found
            |                 |
            v                 v
     Return saved result   Lock both wallet rows
                              |
                              v
                    Check sender's balance
                              |
                              v
                    Debit sender / credit receiver
                              |
                              v
                    Insert DEBIT and CREDIT rows
                              |
                              v
                    Save idempotency result
                              |
                              v
                    Commit database transaction
                              |
                              v
                    Evict both Redis balance keys
                              |
                              v
                    Return transfer response
```

### Example

Assume:

- Sender balance before transfer: ₹10,000
- Receiver balance before transfer: ₹2,000
- Transfer amount: ₹100

Expected result:

| Item | Before | Change | After |
|---|---:|---:|---:|
| Sender | 10,000 | -100 | 9,900 |
| Receiver | 2,000 | +100 | 2,100 |

The ledger should contain two successful rows with the same transaction group ID: one debit of ₹100 and one credit of ₹100.

---

## 8. API overview

The supplied project notes confirm the following operations, but do not provide the complete controller mappings. Use the actual controller annotations as the source of truth and fill in the exact paths before publishing this section.

| Operation | Purpose | Expected behavior |
|---|---|---|
| Register user | Create a user account | Successful registration returns HTTP 201, according to the end-to-end test. |
| Login | Authenticate a user | Valid credentials return an authentication token; invalid credentials should return 401. |
| Create wallet | Create a wallet for the authenticated user | Duplicate wallet type for the same user is rejected. |
| Get balance | Retrieve a wallet balance | Reads Redis first and falls back to PostgreSQL. |
| Transfer | Move funds between wallets | Uses an idempotency key and records debit/credit ledger entries. |
| Transaction history | Retrieve wallet transactions | Supports page and size parameters. |

### Transfer request fields

The service method accepts:

- `senderWalletId`
- `receiverWalletId`
- `amount`
- `idempotencyKey`

The exact JSON property names and HTTP header/body placement must be confirmed from the controller and request DTO.

### Suggested API documentation to add

Once you verify the controller mappings, document each endpoint with:

- HTTP method and path.
- Authentication requirement.
- Request headers.
- Request JSON.
- Success response.
- Validation and error responses.
- A curl example.

Do not publish guessed endpoint paths.

---

## 9. Database design

The project uses PostgreSQL as the persistent data store.

### Confirmed domain records

- **Wallet:** belongs to a user, has a wallet type, and stores a balance.
- **Transaction:** belongs to a wallet and records a transaction group ID, direction, amount, and status.
- **Idempotency key:** stores the request key and the result needed to replay a successful response.
- **User:** represents the account used for authentication and wallet ownership.

The supplied notes refer to five tables in the database, but do not identify all five table names. Confirm the complete schema using PostgreSQL before documenting every table.

### Idempotency table shape from the supplied implementation notes

```sql
CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key      VARCHAR PRIMARY KEY,
    transaction_group_id UUID NOT NULL,
    sender_new_balance   NUMERIC(19,4) NOT NULL,
    status               VARCHAR NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

This is an example from the debugging notes. Confirm that the actual `schema.sql` and entity mappings match it.

### Data type choices

- `UUID` provides identifiers for wallets and transaction groups.
- `NUMERIC(19,4)` is suitable for fixed-precision monetary values in this schema.
- `TIMESTAMPTZ` stores timestamps with time-zone-aware semantics and is appropriate for values mapped to Java `Instant`.
- `VARCHAR` stores statuses and idempotency keys.

For monetary calculations, use Java `BigDecimal`, not `float` or `double`.

---

## 10. Security and reliability

### Authentication

The project uses JWT-based authentication according to the project context. A client authenticates and then sends the token on protected requests.

Typical header format:

```http
Authorization: Bearer <access-token>
```

Confirm the actual token claims, expiry, signing algorithm, secret/key configuration, and security filter implementation in the source code.

### Reliability principles demonstrated

- Database transaction boundaries for related writes.
- Row-level locks for concurrent balance updates.
- Idempotency for safe request retries.
- Double-entry records for transfer traceability.
- Redis fallback so cache failure does not stop database reads.
- Cache eviction after database commit.
- Global exception mapping and server-side logging.
- Container health checks and dependency startup ordering.

### Secrets

Never commit real JWT secrets, database passwords, or production credentials. Keep local values in `.env`, exclude it through `.gitignore` and `.dockerignore`, and commit only a sanitized `.env.example`.

---

## 11. Testing strategy

### Unit tests

Use unit tests for service-level business rules. Mock repositories and Redis when testing the service in isolation.

Important cases:

- Reject null, zero, and negative transfer amounts.
- Reject a transfer to the same wallet.
- Reject a transfer when the sender has insufficient funds.
- Reject or report a missing wallet.
- Verify sender debit and receiver credit.
- Verify two ledger entries share one group ID.
- Verify a repeated idempotency key returns the stored result.
- Verify Redis failures do not fail balance reads or committed transfers.

### Integration tests

Integration tests should verify that Spring, PostgreSQL, Redis, and the application configuration work together. Use an isolated test database and predictable test data.

### End-to-end test

The repository includes `e2e_test.sh`. The supplied output shows that it checks scenarios including:

- User registration.
- Invalid login credentials.
- Authentication token retrieval.
- Duplicate wallet rejection.
- Successful transfer.
- Idempotent replay.
- Negative transfer amount.
- Insufficient funds.
- Missing authorization header.
- Sender balance after transfer.
- Number of ledger rows.
- Paginated transaction history.
- Redis balance caching and cache clearing after transfer.

Run it only after the application is reachable and the required containers are healthy.

A successful run should report zero failed checks. Review the script itself to confirm its assertions and expected status codes.

---

## 12. Debugging and troubleshooting story

This section is useful in interviews because it demonstrates a systematic debugging process rather than only describing features.

### Incident 1: Transfer returned HTTP 500 and did not update balances

**Observed symptoms**

- Transfer returned HTTP 500.
- Sender balance remained unchanged.
- No debit/credit ledger rows were inserted.
- The transaction group ID was null.
- The idempotency record was not created.

**Investigation**

The transfer method performed an idempotency lookup before locking wallets or changing balances. Inspection of the `WalletService` constructor showed that `IdempotencyKeyRepository` was accepted as a parameter but was not assigned to the corresponding instance field.

**Root cause**

The service dependency was not initialized correctly. The first idempotency lookup therefore failed before the transfer could reach its business logic.

**Fix**

Assign the constructor parameter:

```java
this.idempotencyKeyRepository = idempotencyKeyRepository;
```

Then rebuild the application image so the running container contains the corrected code.

**Interview explanation**

> “The transfer was failing before any balance update. I traced the service execution order and found that the idempotency repository was injected through the constructor but not assigned to the field. The first lookup failed, so the transaction never reached the ledger writes. I corrected the constructor and rebuilt the Docker image instead of assuming the running container had picked up the source change.”

### Incident 2: End-to-end tests returned HTTP status `000`

**Observed symptoms**

The test output showed status `000` for registration, login, transfer, and other API calls. Database commands also received empty wallet IDs.

**Interpretation**

For curl, status `000` generally means no HTTP response was received. The app may not have been running, may have crashed during startup, or may not have been reachable on the expected host port. It is not an application-level HTTP status.

**Investigation commands**

```bash
docker compose ps -a
docker compose logs app --tail=100
curl -i http://localhost:8082/api/v1/auth/login
```

**Fix process**

1. Check whether the app container is running or exited.
2. Read the application startup logs.
3. Find the first meaningful `ERROR`, `Caused by`, or Maven compilation failure.
4. Check dependency wiring, schema initialization, environment variables, and port mappings based on that error.
5. Rebuild and rerun the tests only after the app responds.

The supplied notes do not include the final output of these diagnostic commands, so the exact cause of the `000` responses is not established.

### Incident 3: Redis could interfere with a transfer

**Risk identified**

The earlier implementation performed Redis cache deletion inside the database transaction. If Redis was unavailable and the exception propagated, the service could fail and the database transaction could roll back.

**Improvement**

- Use `StringRedisTemplate` for string keys and values.
- Catch Redis read/write/eviction errors.
- Fall back to PostgreSQL for balance reads.
- Register cache eviction with transaction synchronization and run it in `afterCommit()`.

**Interview explanation**

> “I separated cache availability from financial correctness. PostgreSQL commits the transfer first. Redis invalidation happens after commit and is best-effort, so a cache outage does not roll back a valid database transfer.”

### Incident 4: Login with an incorrect password returned the wrong status

**Observed problem**

An invalid-credentials failure could be handled by a generic exception handler and returned as HTTP 500.

**Improvement**

Handle `BadCredentialsException` explicitly and return HTTP 401. Also log unexpected exceptions in the generic handler.

**Why logging matters**

A generic response is appropriate for clients, but developers need the stack trace to diagnose the actual server-side problem. Do not expose stack traces or secrets in the API response.

### Incident 5: Docker networking and Redis configuration

**Potential configuration issue**

Inside a Docker Compose network, the application should connect to Redis using the Compose service name, not `localhost`. `localhost` inside the app container refers to the app container itself.

Example environment settings from the project notes:

```yaml
SPRING_DATA_REDIS_HOST: redis
SPRING_DATA_REDIS_PORT: 6379
SPRING_DATA_REDIS_TIMEOUT: 500ms
```

The Redis service should have a health check, and the app should wait for the Redis service to become healthy if the Compose configuration uses health-based dependency ordering.

### A disciplined debugging method

When an end-to-end test fails:

1. Separate transport failures (`000`) from HTTP errors (`400`, `401`, `409`, `500`).
2. Check container state before changing Java code.
3. Read the earliest relevant exception in the logs.
4. Trace the request through controller → service → repository/cache.
5. Verify database state independently.
6. Make one targeted change.
7. Rebuild the correct image and rerun the failing test.
8. Run the full test suite after the focused test passes.

Useful commands:

```bash
# Container state
docker compose ps -a

# Recent app logs
docker compose logs app --tail=100

# Follow app logs
docker compose logs -f app

# Check API reachability
curl -i http://localhost:8082/

# Check database tables (adjust credentials/database if needed)
docker compose exec postgres psql -U wallet_admin -d wallet_db -c "\dt"

# Check Redis
docker compose exec redis redis-cli ping

# Rebuild and start the stack
docker compose up --build -d
```

---

## 13. Known limitations

Be transparent about these limitations in an interview.

1. **Not production-grade payment infrastructure:** There is no evidence in the supplied project notes of bank/payment-gateway integration, regulatory compliance, settlement, or operational controls.
2. **Idempotency concurrency needs hardening:** A unique key constraint is necessary, and simultaneous requests using the same key must be handled safely.
3. **Request fingerprinting is not established:** The service should detect reuse of the same idempotency key with a different transfer payload.
4. **Cache consistency is best-effort:** Post-commit eviction and TTL do not guarantee strict consistency under every concurrency or Redis failure scenario.
5. **No distributed transaction between PostgreSQL and Redis:** The design intentionally treats Redis as a cache, not as part of the financial transaction.
6. **Observability may be limited:** The supplied notes establish application logging, but do not establish production-grade metrics, distributed tracing, dashboards, or alerting for this wallet service.
7. **API documentation needs verification:** Exact controller paths, request formats, and response examples were not present in the supplied notes.
8. **Deployment scope is local/container-based:** The available notes describe Docker Compose, not a hardened multi-node production deployment.
9. **Security hardening needs review:** Token expiry/rotation, rate limiting, account lockout, audit logging, secret rotation, and authorization checks should be reviewed before production use.
10. **Operational and financial controls are incomplete:** Reconciliation jobs, immutable audit storage, transaction limits, fraud monitoring, and dispute handling are not established by the supplied information.

---

## 14. Future enhancements

Prioritize enhancements based on correctness and security before adding more features.

### Priority 1 — Financial correctness

- Enforce a unique constraint on idempotency keys.
- Handle simultaneous duplicate requests safely.
- Store a request fingerprint and reject key reuse with a different payload.
- Add database constraints for valid amounts, statuses, and relationships.
- Add tests for concurrent transfers and deadlock scenarios.
- Add reconciliation checks that compare ledger totals and wallet balances.

### Priority 2 — Security

- Add role- and ownership-based authorization so users can access only their own wallets.
- Add rate limiting for authentication and transfer endpoints.
- Add refresh-token and token-revocation strategies if required.
- Add secret rotation and stronger environment-specific configuration.
- Add audit logs for sensitive actions.

### Priority 3 — Reliability and operations

- Add metrics for transfer success/failure, latency, database pool usage, and cache hit rate.
- Add distributed tracing and structured logs with correlation IDs.
- Add health/readiness checks and alerts.
- Consider an outbox pattern for reliable post-commit events and cache invalidation.
- Add backup/restore procedures and database migration tooling.

### Priority 4 — Product capabilities

- Add transaction filters by date, direction, and status.
- Add transfer limits and configurable wallet types.
- Add downloadable account statements.
- Add notifications for completed transfers.
- Add an administrative reconciliation and support interface.

---

## 15. Interview preparation

### 15.1 60–90 second project explanation

> “I built a Digital Wallet Backend using Java and Spring Boot. It supports user authentication, wallet creation, balance retrieval, fund transfers, and paginated transaction history.
>
> The main engineering challenge was making transfers reliable. A transfer changes two wallet balances, so I used a database transaction to ensure the debit and credit are committed together. I used pessimistic row locking to protect concurrent balance updates and acquired wallet locks in a consistent order to reduce deadlock risk.
>
> I also implemented a double-entry ledger, where each transfer creates a debit and a credit entry linked by one transaction group ID. To make client retries safe, I added idempotency-key storage so a repeated request can return the original result rather than moving money again.
>
> PostgreSQL is the source of truth, and Redis is used as a cache for balance reads. Redis failures fall back to the database, and cache eviction is performed after a successful database commit. I containerized the services with Docker Compose and used an end-to-end shell script to verify API behavior.”

Only describe a feature as implemented if it is present and working in the final code.

### 15.2 Two-minute technical walkthrough

1. **Request entry:** The client sends an authenticated REST request.
2. **Validation:** The controller validates request structure; the service validates transfer business rules.
3. **Idempotency:** The service checks whether the request key has already been processed.
4. **Concurrency control:** It locks both wallet rows in a deterministic order.
5. **Business rule:** It checks the sender's available balance.
6. **Database updates:** It updates both balances and writes debit/credit ledger entries.
7. **Retry record:** It stores the idempotency result in the same database transaction.
8. **Commit and cache:** After commit, it attempts to evict the affected Redis keys.
9. **Response:** It returns the transaction group ID, sender's new balance, status, and timestamp.

### 15.3 Important interview questions

**Q1. Why do you need a database transaction for a transfer?**

A transfer has multiple related writes. Without a transaction, the sender could be debited while the receiver is not credited. A transaction makes the database changes atomic.

**Q2. What is the difference between atomicity and consistency?**

Atomicity means all operations in the transaction happen or none do. Consistency means a committed transaction preserves the database's defined rules and invariants.

**Q3. Why use `BigDecimal` for money?**

Binary floating-point types such as `double` cannot represent many decimal fractions exactly. `BigDecimal` provides decimal arithmetic with explicit precision and scale.

**Q4. What problem does pessimistic locking solve?**

It prevents another transaction from modifying a locked wallet row while the current transfer checks and updates its balance. This helps avoid lost updates and overspending caused by concurrent requests.

**Q5. Why lock wallets in a fixed order?**

Two transfers could otherwise lock the same wallets in opposite orders. A consistent order reduces circular waiting and therefore reduces deadlock risk.

**Q6. What is a double-entry ledger?**

Each transfer creates a debit entry for the sender and a credit entry for the receiver. Both entries share a transaction group ID, making the transfer traceable.

**Q7. What is idempotency?**

An operation is idempotent when repeating it with the same request identity does not produce additional side effects. Here, a retry with the same idempotency key should return the original transfer result.

**Q8. Is checking the idempotency key before processing enough?**

Not by itself. Two simultaneous requests can both observe that the key is absent. A database uniqueness constraint and duplicate-conflict handling are needed to make the design safe under concurrency.

**Q9. Why use Redis if PostgreSQL already stores the balance?**

Redis can reduce latency and database load for repeated reads. It is a cache only; PostgreSQL remains authoritative.

**Q10. What happens if Redis goes down?**

Balance reads should fall back to PostgreSQL. A transfer should still commit in PostgreSQL, and cache eviction failure should be logged rather than propagated as a transfer failure.

**Q11. Why evict Redis after commit?**

Evicting before commit can allow a rollback or concurrent read to leave an incorrect cached value. After-commit eviction ensures the database transaction has succeeded before invalidation is attempted.

**Q12. What is the cache-aside pattern?**

The application checks the cache first. On a miss, it reads from the database, returns the result, and attempts to populate the cache.

**Q13. What is the difference between 401 and 403?**

401 means the request lacks valid authentication credentials. 403 means the server understood the request but refuses authorization. Exact behavior can depend on the security configuration.

**Q14. Why use DTOs instead of returning entities?**

DTOs keep the external API contract separate from persistence structure, prevent accidental exposure of internal fields, and make request/response validation easier.

**Q15. What does pagination solve?**

It limits the amount of transaction history returned in one response, improving memory usage, response size, and database efficiency.

**Q16. How did you debug the transfer failure?**

I followed the request execution path and checked the first operation in the service. The idempotency repository was accepted by the constructor but not assigned to the field, so the first lookup failed before any wallet update. I corrected the assignment and rebuilt the container image.

**Q17. What did HTTP status `000` mean in your test output?**

It was curl's indication that it did not receive an HTTP response. I checked the container state, application logs, and port reachability before treating it as an API logic failure.

**Q18. What would you improve next?**

I would first harden idempotency under concurrent duplicate requests, add request fingerprinting, test concurrent transfers, and add reconciliation and observability. Those improvements protect correctness before adding more product features.

### 15.4 Concepts to revise before the interview

- Java: OOP, collections, exceptions, immutability, `BigDecimal`, UUID.
- Spring: dependency injection, bean lifecycle basics, REST controllers, validation, `@Transactional`, exception advice.
- Databases: ACID, isolation, row locks, indexes, unique constraints, foreign keys, deadlocks, `NUMERIC`, `TIMESTAMPTZ`.
- Distributed systems: retries, idempotency, cache-aside, cache invalidation, eventual consistency.
- Security: authentication vs authorization, JWT, password hashing, 401 vs 403.
- Docker: images vs containers, Dockerfile stages, Compose networking, health checks, environment variables.
- Testing: unit vs integration vs end-to-end tests, mocks, test data setup.

---

## 16. Run the project locally

### Prerequisites

Install:

- Git
- JDK 21
- Maven 3.9 or compatible
- Docker Engine
- Docker Compose plugin

Check installations:

```bash
java -version
mvn -version
docker --version
docker compose version
git --version
```

### 1. Clone the repository

Replace the placeholder with the actual Git repository URL:

```bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd wallet-app
```

### 2. Configure environment variables

Create your local environment file from the example:

```bash
cp .env.example .env
```

Edit `.env` and provide the values required by your application. The exact variable names must match the configuration in your project.

Typical configuration categories include:

- PostgreSQL database name, username, and password.
- JWT signing secret/key.
- Application profile.
- Host port, if configurable.

Do not use real production secrets in a local example or commit `.env` to Git.

### 3. Check Docker Compose configuration

Confirm that the Compose file defines the required application, PostgreSQL, and Redis services.

The application container should use the Compose service name for Redis (for example, `redis`) rather than `localhost`. Confirm the PostgreSQL hostname in the same way.

### 4. Build and start the stack

If the Dockerfile contains a Maven build stage:

```bash
docker compose up --build -d
```

If the Dockerfile only copies an already-built JAR, first run:

```bash
mvn clean package -DskipTests
docker compose up --build -d
```

### 5. Verify containers and logs

```bash
docker compose ps -a
docker compose logs app --tail=100
docker compose logs -f app
```

Wait for the application startup message and verify that the app, PostgreSQL, and Redis are healthy.

### 6. Verify dependencies

```bash
docker compose exec redis redis-cli ping
docker compose exec postgres psql -U wallet_admin -d wallet_db -c "\dt"
```

Adjust the PostgreSQL username and database name if your Compose configuration uses different values.

### 7. Run the end-to-end test

```bash
chmod +x e2e_test.sh
./e2e_test.sh
```

The API must be reachable on the port expected by the script. The supplied debugging output used port `8082`; verify that the current Compose port mapping and script agree.

### 8. Stop the stack

```bash
docker compose down
```

To remove the local database volume as well:

```bash
docker compose down -v
```

**Warning:** `docker compose down -v` deletes the persisted local database volume and its data. Use it only when you intentionally want a clean database.

---

## 17. Reproduce the project on another system

Use this checklist when setting up the project on a new laptop or another developer's machine.

### A. Repository and prerequisites

1. Install Git, JDK 21, Maven, Docker, and the Docker Compose plugin.
2. Clone the repository.
3. Enter the project directory.
4. Confirm the Java and Docker versions.

```bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd wallet-app

java -version
mvn -version
docker --version
docker compose version
```

### B. Configuration

5. Copy the sample environment file.

```bash
cp .env.example .env
```

6. Add local database credentials and a local JWT secret.
7. Confirm `.env` is ignored by Git.
8. Check that `docker-compose.yml` uses service names for inter-container connections.
9. Confirm the app's port mapping matches the API URL used by `e2e_test.sh`.

### C. Build and startup

10. Inspect the Dockerfile to determine whether it builds the JAR itself.

```bash
cat Dockerfile
```

11. If it has a Maven build stage, run:

```bash
docker compose up --build -d
```

12. If it expects a local JAR, run:

```bash
mvn clean package -DskipTests
docker compose up --build -d
```

13. Check that all containers are healthy.

```bash
docker compose ps -a
docker compose logs app --tail=100
```

### D. Verify functionality

14. Verify Redis:

```bash
docker compose exec redis redis-cli ping
```

15. Verify PostgreSQL and the schema:

```bash
docker compose exec postgres psql -U wallet_admin -d wallet_db -c "\dt"
```

16. Run the end-to-end test:

```bash
chmod +x e2e_test.sh
./e2e_test.sh
```

17. If a test fails, inspect the first relevant application exception before changing code:

```bash
docker compose logs app --tail=200
```

18. Confirm the complete test output has zero failures before considering the setup reproduced.

### E. Clean shutdown

```bash
docker compose down
```

Use `docker compose down -v` only if you intentionally want to delete the database volume.

### Reproduction checklist

- [ ] Repository cloned successfully.
- [ ] Required software installed.
- [ ] `.env` created locally.
- [ ] Secrets are not committed.
- [ ] Docker image built successfully.
- [ ] App, PostgreSQL, and Redis are running and healthy.
- [ ] Database schema is initialized.
- [ ] API is reachable on the configured port.
- [ ] End-to-end test completes with zero failures.

---

## 18. Useful commands

```bash
# Build without running tests
mvn clean package -DskipTests

# Validate Maven project
mvn validate

# Start or rebuild containers
docker compose up --build -d

# Stop containers
docker compose down

# Stop containers and delete volumes (destructive to local DB data)
docker compose down -v

# Container status
docker compose ps -a

# App logs
docker compose logs app --tail=100

# Follow app logs
docker compose logs -f app

# Redis health
docker compose exec redis redis-cli ping

# PostgreSQL table list
docker compose exec postgres psql -U wallet_admin -d wallet_db -c "\dt"

# End-to-end test
./e2e_test.sh
```

---

## Final interview reminder

The strongest part of this project is not simply that it exposes CRUD APIs. It is the reasoning behind reliable money movement:

- **Atomicity:** related database writes commit together.
- **Concurrency control:** row locks protect balance updates.
- **Auditability:** debit and credit entries form a double-entry ledger.
- **Retry safety:** idempotency keys prevent duplicate processing when correctly enforced under concurrency.
- **Resilience:** Redis improves read performance but does not own financial state.
- **Debuggability:** logs, container inspection, and repeatable tests help isolate failures.

Be ready to draw the transfer workflow, explain what happens when each step fails, and discuss the limitations honestly.
