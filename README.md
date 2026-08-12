# Digital Wallet & Payment Ledger Backend

A production-style digital wallet system built with Spring Boot and PostgreSQL, supporting user-to-user money transfers backed by a double-entry ledger, JWT authentication, and role-based access control — containerized with Docker Compose and paired with a React frontend.

## Why this project

Most "wallet clone" tutorials skip the parts that actually matter in a real payments system: preventing race conditions during concurrent transfers, keeping a permanent auditable transaction history, and securing money-moving endpoints properly. This project focuses specifically on those problems.

## Tech Stack

- **Backend:** Java 21, Spring Boot 4, Spring Data JPA, Spring Security
- **Database:** PostgreSQL 17
- **Auth:** JWT (JJWT library), BCrypt password hashing
- **Containerization:** Docker, Docker Compose
- **Frontend:** React (CDN-based, single-file)
- **Testing:** JUnit 5, Mockito

## Core Features

- User registration and login with JWT-based authentication
- Role-based access control (USER / ADMIN)
- Wallet creation with Primary and Savings wallet types
- User-to-user money transfers with:
  - **Pessimistic locking** (`SELECT ... FOR UPDATE`) to prevent race conditions on concurrent balance updates
  - **Deterministic lock ordering** to prevent deadlocks when two transfers touch the same pair of wallets in opposite directions
  - **Double-entry ledger** — every transfer creates two linked, append-only transaction rows (one DEBIT, one CREDIT), never a mutated balance-only record
- Idempotency key support to guard against duplicate transfer submissions on client retry
- Transaction history lookup per wallet
- Centralized exception handling returning clean, consistent JSON error responses
- Automated concurrency test simulating simultaneous transfers to validate locking correctness

## Architecture

Layered architecture, strictly one-directional:

```
Controller → Service → Repository → Database
```

- **Controller layer** — HTTP concerns only (routing, request/response DTOs, status codes)
- **Service layer** — business logic, transaction boundaries (`@Transactional`), locking, validation
- **Repository layer** — Spring Data JPA interfaces, no business logic

## Database Schema

- `users` — id (UUID), email, password_hash, role
- `wallets` — id (UUID), user_id (FK), type (PRIMARY/SAVINGS), balance (NUMERIC), version (optimistic lock support), UNIQUE(user_id, type)
- `transactions` — id (UUID), transaction_group_id, wallet_id (FK), direction (DEBIT/CREDIT), amount, status, original_transaction_id (nullable, self-referencing FK for refund traceability)
- `notifications` — id (UUID), user_id (FK), type, message, is_read
- `idempotency_keys` — idempotency_key (PK, client-supplied), transaction_group_id, sender_new_balance, status

Money fields use `NUMERIC(19,4)`, never `FLOAT`/`DOUBLE`, to avoid binary floating-point rounding errors in financial calculations.

## Running the Project (Docker Compose)

### Prerequisites
- Docker and Docker Compose installed

### 1. Clone and configure

```bash
git clone <your-repo-url>
cd wallet-app
```

Create a `.env` file at the project root (never committed — see `.gitignore`):

```
DB_PASSWORD=your_local_db_password
JWT_SECRET=a_long_random_string_at_least_32_characters
```

### 2. Start everything

```bash
docker compose up --build
```

This builds the Spring Boot app image, starts PostgreSQL, waits for the database to be healthy, auto-applies `schema.sql`, then starts the app — all in one command.

Confirm both containers are healthy:
```bash
docker compose ps
```

### 3. Run the frontend

Open `frontend/wallet-frontend-complete.html` directly in a browser — no build step required. If your backend's `server.port` differs from the default, update the `API_BASE` constant at the top of the file to match.

## Testing the Application

### Via curl / Postman

**1. Register:**
```bash
curl -X POST http://localhost:8082/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"password123"}'
```
Expect `201 Created`.

**2. Login:**
```bash
curl -X POST http://localhost:8082/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"password123"}'
```
Expect `200 OK` with `{ "token": "..." }`. Copy the token for the next steps.

**3. Create a wallet:**
```bash
curl -X POST http://localhost:8082/api/v1/wallets \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"type":"PRIMARY"}'
```
Expect `201 Created` with the new wallet's `id` and `balance: 0`. Repeat registration/login/wallet-creation for a second user to have two wallets to transfer between.

**4. Transfer money:**
```bash
curl -X POST http://localhost:8082/api/v1/wallets/transfer \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"senderWalletId":"...","receiverWalletId":"...","amount":100,"idempotencyKey":"test-1"}'
```
Expect `201 Created`. Verify both wallet balances updated and two linked `transactions` rows exist (DEBIT + CREDIT, same `transaction_group_id`).

**5. Negative tests:**

| Test | Expected |
|---|---|
| No `Authorization` header | `403 Forbidden` |
| `amount: -50` | `400 Bad Request` |
| Amount greater than sender's balance | `409 Conflict` |
| Wrong password on login | `401 Unauthorized` (clean message, not a raw 500) |
| Same `idempotencyKey` sent twice | Same response both times, no double debit |
| Nonexistent `senderWalletId` | `404 Not Found` |

**6. Transaction history:**
```bash
curl http://localhost:8082/api/v1/wallets/<walletId>/transactions \
  -H "Authorization: Bearer <token>"
```
Expect `200 OK` with an array of that wallet's transactions.

### Automated concurrency test

```bash
mvn test -Dtest=WalletConcurrencyTest
```
Fires 20 simultaneous transfer requests across a 10-thread pool between the same two wallets, then asserts the final balances are mathematically exact — proving the pessimistic locking prevents lost updates under real concurrent load.

### Via the frontend

Open the HTML file → Register → Login → Wallets tab (create a wallet, copy its ID) → repeat for a second user → Transfer tab (paste both IDs, send) → History tab (paste a wallet ID, confirm the ledger entry appears).

## Known Limitations / Future Work

Documented honestly rather than hidden:

- **Idempotency enforcement** currently uses a check-then-save pattern, which has a narrow race window if two identical requests arrive at the exact same instant. A fully rigorous implementation would claim the idempotency key (insert it) *before* acquiring wallet locks, turning the database's own unique-constraint violation into the duplicate-detection mechanism.
- **No refund endpoint yet** — the schema supports it (`original_transaction_id` self-reference on `transactions`), but the service-layer logic isn't implemented.
- **No pagination on transaction history** — for a wallet with many transactions, the endpoint currently returns everything at once.
- **No caching layer (Redis)** — every read hits Postgres directly; frequently-accessed data like wallet balances isn't cached.
- **No async messaging (Kafka/RabbitMQ)** — notifications are modeled in the schema but not actually sent; a real implementation would decouple notification delivery from the transfer request path.
- **Limited automated test coverage** — a concurrency test and basic unit tests exist for the service layer, but repository-level integration tests (ideally via Testcontainers against a real Postgres instance) are not yet built.
- **Frontend is intentionally minimal** — single-file React with CDN-loaded dependencies and no routing library, built for functional demonstration of the full API surface rather than production polish.
- **CORS is currently permissive (`allowedOriginPatterns("*")`)** for local development convenience; this would be tightened to specific allowed origins before any real deployment.

## Uploading to GitHub

```bash
cd wallet-app
git init
```

Create a `.gitignore`:
```
target/
.env
*.class
.idea/
*.iml
```

```bash
git add .
git commit -m "Wallet backend with locking, ledger, JWT auth, Docker, and React frontend"
```

Create a repository on GitHub (no README/license/gitignore selected, since local files already exist), then:

```bash
git remote add origin https://github.com/<your-username>/wallet-app.git
git branch -M main
git push -u origin main
```

**Before pushing, double-check:**
- `.env` is not staged (`git status` should not show it)
- No hardcoded passwords or JWT secrets exist in any committed file — only `${ENV_VAR}` placeholders
