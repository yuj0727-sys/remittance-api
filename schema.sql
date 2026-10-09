CREATE TABLE customers (customer_id VARCHAR(10) PRIMARY KEY, name VARCHAR(100) NOT NULL);
INSERT INTO customers (customer_id, name) VALUES ('C001','Kim'),('C002','Lee'),('C003','Park');

CREATE TABLE transfers (
    transfer_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(10) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    send_currency VARCHAR(3) NOT NULL,
    receive_currency VARCHAR(3) NOT NULL,
    send_amount BIGINT NOT NULL,
    fee BIGINT NOT NULL,
    total_debit BIGINT NOT NULL,
    receive_amount DECIMAL(19,2) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- Stops two transfers from sharing the same id.
    CONSTRAINT pk_transfers PRIMARY KEY (transfer_id),
    -- Stops a transfer for a customer that does not exist.
    CONSTRAINT fk_transfers_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    -- Stops one customer from reusing the same idempotency key.
    CONSTRAINT uk_transfers_customer_idempotency UNIQUE (customer_id, idempotency_key),
    -- Stops a send amount below 10000 KRW.
    CONSTRAINT ck_transfers_send_amount CHECK (send_amount >= 10000),
    -- Stops a fee below 3000 KRW.
    CONSTRAINT ck_transfers_fee CHECK (fee >= 3000),
    -- Stops a total debit that is not send amount plus fee.
    CONSTRAINT ck_transfers_total_debit CHECK (total_debit = send_amount + fee),
    -- Stops a status outside REQUESTED, SENDING, COMPLETED, FAILED, CANCELLED.
    CONSTRAINT ck_transfers_status CHECK (status IN ('REQUESTED', 'SENDING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

-- Stops a slow full scan when a later daily limit is checked by customer and time.
CREATE INDEX idx_transfers_customer_created_at ON transfers (customer_id, created_at);
