DROP TABLE IF EXISTS ledger_entries;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS assets;

CREATE TABLE assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    asset_id UUID NOT NULL,
    type VARCHAR(10) CHECK (type IN ('USER', 'SYSTEM')),
    balance BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_asset FOREIGN KEY (asset_id) REFERENCES assets(id)
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(10) DEFAULT 'SUCCESS' CHECK(status IN ('SUCCESS', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    transaction_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

CREATE UNIQUE INDEX uq_user_asset ON accounts (user_id, asset_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_system_asset ON accounts (asset_id) WHERE type = 'SYSTEM';
CREATE INDEX idx_ledger_account ON ledger_entries (account_id, created_at);
CREATE INDEX idx_ledger_transaction ON ledger_entries (transaction_id);

BEGIN;

INSERT INTO assets (id, name, code) VALUES
('11111111-1111-1111-1111-111111111111', 'Gold Coins', 'GOLD_COINS'),
('22222222-2222-2222-2222-222222222222', 'Diamonds', 'DIAMONDS'),
('33333333-3333-3333-3333-333333333333', 'Loyalty Points', 'LOYALTY_POINTS');

INSERT INTO users (id, name, email) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Aditi Singh', 'aditisingh@gmail.com'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Priya Jha', 'priyajha@gmail.com');

INSERT INTO accounts (id, asset_id, type, balance) VALUES
('aaaa1111-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'SYSTEM', 0),
('aaaa1111-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'SYSTEM', 0),
('aaaa1111-0000-0000-0000-000000000003', '33333333-3333-3333-3333-333333333333', 'SYSTEM', 0);

INSERT INTO accounts (id, user_id, asset_id, type, balance) VALUES
('bbbb2222-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'USER', 0),
('bbbb2222-0000-0000-0000-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 'USER', 0),
('bbbb2222-0000-0000-0000-000000000003', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111', 'USER', 0),
('bbbb2222-0000-0000-0000-000000000004', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', 'USER', 0);

INSERT INTO transactions (id, type, idempotency_key, status)
VALUES ('cccc3333-0000-0000-0000-000000000001', 'TOPUP', 'seed-aditi-gold', 'SUCCESS');

INSERT INTO ledger_entries (id, account_id, type, amount, transaction_id) VALUES
('dddd4444-0000-0000-0000-000000000001', 'aaaa1111-0000-0000-0000-000000000001', 'DEBIT', 500, 'cccc3333-0000-0000-0000-000000000001'),
('dddd4444-0000-0000-0000-000000000002', 'bbbb2222-0000-0000-0000-000000000001', 'CREDIT', 500, 'cccc3333-0000-0000-0000-000000000001');

UPDATE accounts
SET balance = balance - 500
WHERE id = 'aaaa1111-0000-0000-0000-000000000001';

UPDATE accounts
SET balance = balance + 500
WHERE id = 'bbbb2222-0000-0000-0000-000000000001';

INSERT INTO transactions (id, type, idempotency_key, status)
VALUES ('cccc3333-0000-0000-0000-000000000002', 'TOPUP', 'seed-priya-gold', 'SUCCESS');

INSERT INTO ledger_entries (id, account_id, type, amount, transaction_id) VALUES
('dddd4444-0000-0000-0000-000000000003', 'aaaa1111-0000-0000-0000-000000000001', 'DEBIT', 300, 'cccc3333-0000-0000-0000-000000000002'),
('dddd4444-0000-0000-0000-000000000004', 'bbbb2222-0000-0000-0000-000000000003', 'CREDIT', 300, 'cccc3333-0000-0000-0000-000000000002');

UPDATE accounts
SET balance = balance - 300
WHERE id = 'aaaa1111-0000-0000-0000-000000000001';

UPDATE accounts
SET balance = balance + 300
WHERE id = 'bbbb2222-0000-0000-0000-000000000003';

COMMIT;