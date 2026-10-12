# PayPulse

A ledger-based wallet service built for high-traffic applications like gaming platforms or loyalty reward systems. This service manages application-specific virtual credits (Gold Coins, Diamonds, Loyalty Points) with full transactional integrity, concurrency safety, and idempotent operations.

## Attribution

PayPulse is based on [InternalWalletService](https://github.com/Muskan244/InternalWalletService) by [Muskan244](https://github.com/Muskan244).

## Live Deployment

- **Swagger UI:** [Render URL](https://internalwalletservice.onrender.com/swagger-ui/index.html)

## Tech Stack & Reasoning

| Technology | Why |
|---|---|
| **Java 21 + Spring Boot 4** | Mature ecosystem for building transactional backend services. `@Transactional` provides declarative transaction management, and Spring Data JPA eliminates boilerplate database code. |
| **PostgreSQL 16** | Industry standard relational database with excellent ACID transaction support, row-level locking (`SELECT ... FOR UPDATE`), and robust concurrency handling critical for a financial service. |
| **Docker + Docker Compose** | One-command setup (`docker-compose up`) ensures the project run without installing Java or PostgreSQL locally. |
| **Double-Entry Ledger** | Instead of simply updating a `balance` column, every transaction creates two ledger entries (one debit, one credit). This provides full auditability and ensures the system is always internally consistent, the sum of all ledger entries across all accounts is always zero. |

## Architecture

```
Controller Layer (REST API)
    ↓ validates input, returns HTTP responses
Service Layer (Business Logic)
    ↓ handles transaction flows, locking, idempotency
Repository Layer (Database Access)
    ↓ JPA repositories with pessimistic locking support
PostgreSQL (ACID Transactions)
```

### Database Schema

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────┐
│   assets     │     │    users     │     │    accounts       │
├──────────────┤     ├──────────────┤     ├──────────────────┤
│ id (PK)      │     │ id (PK)      │     │ id (PK)          │
│ name         │◄────┤ name         │     │ user_id (FK)     │──► users
│ code (UNIQUE)│     │ email(UNIQUE)│     │ asset_id (FK)    │──► assets
│ created_at   │     │ created_at   │     │ type (USER/SYSTEM)│
└──────────────┘     └──────────────┘     │ balance          │
                                          │ created_at       │
                                          └──────────────────┘
                                                   │
                                                   ▼
┌──────────────────────---┐     ┌──────────────────────┐
│   transactions          │     │   ledger_entries     │
├──────────────────────---┤     ├──────────────────────┤
│ id (PK)                 │     │ id (PK)              │
│ type (TOPUP/BONUS/      │◄────│ transaction_id (FK)  │
│      SPEND)             │     │ account_id (FK)      │──► accounts
│ idempotency_key(UNIQUE) │     │ type (CREDIT/DEBIT)  │
│ status                  │     │ amount               │
│ created_at              │     │ created_at           │
└──────────────────────---┘     └──────────────────────┘
```

**Key design decisions:**
- **`accounts.balance`** is a cached value for fast reads. The source of truth is always the ledger entries.
- **System accounts** (Treasury) have `user_id = NULL` and `type = SYSTEM`. They act as the source/destination for all credit movements.
- **Every transaction creates exactly 2 ledger entries** — one debit and one credit ensuring the system always sums to zero.

## Quick Start

### Option 1: Docker Compose

```bash
cd PayPulse
docker-compose up --build
```

This automatically:
1. Starts a PostgreSQL 16 database
2. Runs the `seed.sql` script (creates tables + inserts initial data)
3. Starts the Spring Boot application on port 8080

The API will be available at `http://localhost:8080`

Easily accessible through Swagger UI at `http://localhost:8080/swagger-ui.html`

### Option 2: Manual Setup

**Prerequisites:** Java 21, PostgreSQL 16

```bash
# 1. Create the database
createdb paypulse

# 2. Run the seed script
psql -d paypulse -f src/main/resources/seed.sql

# 3. Start the application
./gradlew bootRun
```

## Seed Data

The seed script creates the following initial state:

**Asset Types:**

| Code | Name |
|---|---|
| GOLD_COINS | Gold Coins |
| DIAMONDS | Diamonds |
| LOYALTY_POINTS | Loyalty Points |

**Users:**

| Name | Email |
|---|---|
| Aditi Singh | aditisingh@gmail.com |
| Priya Jha | priyajha@gmail.com |

**Initial Balances:**
- Aditi: 500 Gold Coins (backed by a seed TOPUP transaction + ledger entries)
- Priya: 300 Gold Coins (backed by a seed TOPUP transaction + ledger entries)
- Treasury (SYSTEM): -800 Gold Coins (net outflow to users, this is expected for a system account)

## API Endpoints

### 1. Process Transaction

**`POST /api/v1/transactions`**

Handles all three transaction flows: top-up, bonus, and spend.

**Request Body:**
```json
{
  "type": "TOPUP",
  "email": "aditisingh@gmail.com",
  "assetCode": "GOLD_COINS",
  "amount": 100,
  "idempotencyKey": "unique-key-123"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| type | String | Yes | `TOPUP`, `BONUS`, or `SPEND` |
| email | String | Yes | User's email address |
| assetCode | String | Yes | Asset code (e.g., `GOLD_COINS`) |
| amount | Long | Yes | Amount (must be positive) |
| idempotencyKey | String | Yes | Unique key to prevent duplicate processing |

**Success Response:** `201 Created` the first time the key is seen, `200 OK` when the same key and the same request are sent again.

```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "transactionType": "TOPUP",
  "amount": 100,
  "balanceAfter": 600,
  "status": "SUCCESS",
  "createdAt": "2026-02-22T12:00:00Z"
}
```

**Error Responses:**
- `400` — Insufficient balance, invalid amount, missing required fields
- `404` — User not found, asset not found, account not found
- `409` — The idempotency key was already used for a different request, or the account lock could not be acquired in time

### 2. Get Balance

**`GET /api/v1/wallet/balance?email={email}&assetCode={assetCode}`**

The same balance is also available at `GET /api/v1/balance`.

**Success Response (200):**
```json
{
  "userId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "email": "aditisingh@gmail.com",
  "assetName": "Gold Coins",
  "balance": 600
}
```

### 3. Get Transaction History

**`GET /api/v1/transactions?email={email}`**

Returns all transactions for a user in reverse chronological order, including the amount that moved on that user's account. An unknown email returns `404`.

## Concurrency Strategy

### Problem

In a high-traffic system, two requests can arrive simultaneously to spend from the same wallet. Without protection:
1. Both read balance as 100
2. Both check "is 100 >= 80?" - both pass
3. Both debit 80
4. Balance goes to -60 - data corruption

### Solution: Pessimistic Locking with Consistent Lock Ordering

**Pessimistic Locking:** Before modifying any account balance, top-up, bonus, and spend all acquire a row-level lock using `SELECT ... FOR UPDATE` (via JPA's `@Lock(LockModeType.PESSIMISTIC_WRITE)`). The second concurrent request blocks until the first completes, then reads the updated balance. A lock that is still held after 5 seconds fails the request with `409` so the caller can retry.

```
Request 1: BEGIN → LOCK account (acquires lock) → check balance → debit → COMMIT
Request 2: BEGIN → LOCK account (waits...) ────────────────────────────→ (acquires lock) → check balance → sees updated balance → proceeds
```

**Deadlock Avoidance:** Every transaction involves two accounts (e.g., user account + Treasury). If two transactions lock these accounts in different orders, a deadlock can occur. To prevent this, accounts are always locked in ascending UUID order regardless of the transaction direction. This eliminates circular wait conditions.

## Idempotency Strategy

### Problem

Network failures can cause clients to retry requests. Without protection, the same top-up could be applied multiple times.

### Solution: Idempotency Key

Every request includes a client-generated `idempotencyKey`. Before processing:
1. Check if a transaction with this key already exists in the database
2. If yes → return the original result without reprocessing
3. If no → process normally and store the key

The `idempotency_key` column has a **UNIQUE constraint** in the database as a safety net even if the application-level check has a race condition, the database prevents duplicate insertions. Replaying a key returns the original transaction and the balance that ledger entries showed at that time. Reusing a key with a different type, amount, email, or asset returns `409`.

`seed.sql` runs only when the Postgres volume is first created. After a schema change, recreate it with `docker-compose down -v` before starting again.
