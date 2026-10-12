# PayPulse

PayPulse is a ledger-based wallet service designed for high-traffic applications such as gaming platforms and loyalty reward systems. It manages virtual credits like Gold Coins, Diamonds, and Loyalty Points while ensuring transactional integrity, concurrency safety, and idempotent operations.

## Live Deployment

- **Swagger UI:** [Explore API Documentation](https://internalwalletservice.onrender.com/swagger-ui/index.html)

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 21 + Spring Boot | REST API development and transaction management |
| PostgreSQL 16 | Relational data storage and ACID transactions |
| Spring Data JPA | Database operations and pessimistic locking |
| Docker + Docker Compose | Containerization and simplified setup |
| Double-Entry Ledger | Auditable and consistent wallet transactions |

## Architecture

```text
REST Controllers
      ↓
Service Layer
      ↓
Repository Layer
      ↓
PostgreSQL Database
```

- **Controller Layer:** Handles API requests, input validation, and HTTP responses.
- **Service Layer:** Implements wallet operations, transaction processing, concurrency control, and idempotency.
- **Repository Layer:** Manages database access and row-level locking.
- **Database:** Stores accounts, assets, transactions, and ledger entries.

## Key Features

- **Double-Entry Ledger:** Records every transaction using corresponding debit and credit entries.
- **Transactional Integrity:** Uses database transactions to maintain consistent wallet balances.
- **Concurrency Control:** Implements pessimistic locking using `SELECT ... FOR UPDATE` to prevent conflicting updates.
- **Deadlock Prevention:** Acquires account locks in a consistent UUID order.
- **Idempotent Operations:** Uses unique idempotency keys to prevent duplicate transaction processing.
- **Multiple Virtual Assets:** Supports Gold Coins, Diamonds, and Loyalty Points.
- **Transaction History:** Provides transaction records in reverse chronological order.
- **REST APIs:** Supports wallet balance retrieval and transaction processing.

## Database Design

The database consists of the following core entities:

- **Assets:** Stores supported virtual asset types.
- **Accounts:** Maintains wallet balances and identifies user or system accounts.
- **Transactions:** Records transaction types, statuses, timestamps, and idempotency keys.
- **Ledger Entries:** Stores individual debit and credit records associated with transactions.

The account balance is maintained for fast reads, while ledger entries provide an auditable record of balance movements.

Every successful transaction records a debit and a corresponding credit, maintaining ledger consistency.

## API Endpoints

### 1. Process Transaction

`POST /api/v1/transactions`

Supports three transaction types:

- `TOPUP` — Adds credits to a wallet.
- `BONUS` — Credits promotional rewards.
- `SPEND` — Deducts credits from a wallet.

Example request:

```json
{
  "type": "TOPUP",
  "email": "example@email.com",
  "assetCode": "GOLD_COINS",
  "amount": 100,
  "idempotencyKey": "unique-key-123"
}
```

The API returns the transaction ID, transaction type, amount, resulting balance, status, and creation timestamp.

### 2. Get Wallet Balance

`GET /api/v1/wallet/balance?email={email}&assetCode={assetCode}`

Returns the wallet balance for a specified asset.

### 3. Get Transaction History

`GET /api/v1/transactions?email={email}`

Returns the transaction history associated with a wallet in reverse chronological order.

## Concurrency Strategy

Concurrent requests can attempt to spend from the same wallet simultaneously, potentially causing incorrect balances.

PayPulse addresses this using **pessimistic locking** with JPA's `@Lock(LockModeType.PESSIMISTIC_WRITE)`. A request acquires the required account locks before checking or modifying balances.

Account locks are acquired in ascending UUID order to reduce the risk of deadlocks. Requests that exceed the configured lock wait time can fail and be retried.

## Idempotency Strategy

Every transaction request includes a client-generated idempotency key.

1. Check whether the key has already been processed.
2. If it exists and the request matches, return the original transaction result.
3. If it is new, process the transaction and persist the result.
4. If the key is reused with different request details, reject the request.

A database `UNIQUE` constraint on the idempotency key provides an additional safeguard against duplicate processing.

## Quick Start

### Using Docker Compose

```bash
docker-compose up --build
```

This starts PostgreSQL, initializes the database and seed data, and launches the Spring Boot application.

- **API Base URL:** `http://localhost:8080`
- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`

### Manual Setup

**Prerequisites:** Java 21 and PostgreSQL 16.

```bash
createdb paypulse
psql -d paypulse -f src/main/resources/seed.sql
./gradlew bootRun
```

Refer to the project's configuration files for the required database settings.
