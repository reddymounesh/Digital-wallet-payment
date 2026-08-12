-- Drop tables in reverse dependency order (optional)


-------------------------------------------------------
-- USERS
-------------------------------------------------------
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-------------------------------------------------------
-- WALLETS
-------------------------------------------------------
CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    balance NUMERIC(19,4) NOT NULL,
    version BIGINT,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_wallet_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_wallet_user_type
        UNIQUE(user_id, type)
);

-------------------------------------------------------
-- TRANSACTIONS
-------------------------------------------------------
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    transaction_group_id UUID NOT NULL,

    wallet_id UUID NOT NULL,

    direction VARCHAR(20) NOT NULL,

    amount NUMERIC(19,4) NOT NULL,

    status VARCHAR(20) NOT NULL,

    original_transaction_id UUID,

    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_transaction_wallet
        FOREIGN KEY (wallet_id)
        REFERENCES wallets(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_original_transaction
        FOREIGN KEY (original_transaction_id)
        REFERENCES transactions(id)
);

-------------------------------------------------------
-- NOTIFICATIONS
-------------------------------------------------------
CREATE TABLE notifications (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    type VARCHAR(30) NOT NULL,

    message TEXT NOT NULL,

    is_read BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-------------------------------------------------------
-- IDEMPOTENCY KEYS
-------------------------------------------------------
CREATE TABLE idempotency_keys (
    idempotencykey VARCHAR(255) PRIMARY KEY,

    transaction_group_id UUID NOT NULL,

    sender_new_balance NUMERIC(19,4) NOT NULL,

    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMP NOT NULL
);

-------------------------------------------------------
-- INDEXES
-------------------------------------------------------
