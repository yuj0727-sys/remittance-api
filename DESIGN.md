# Design

## Q1. Tables

`customers` has only `customer_id` and `name`. Seed rows are C001 Kim, C002 Lee, and C003 Park.

`transfers` is one remittance. The primary key on `transfer_id` stops two rows from sharing an id. The foreign key on `customer_id` stops a transfer for a customer that does not exist. `UNIQUE (customer_id, idempotency_key)` stops one customer from storing the same key twice, so only one of two concurrent inserts can commit. Checks stop a send amount below 10000, a fee below 3000, a total debit that is not send amount plus fee, and a status outside REQUESTED, SENDING, COMPLETED, FAILED, and CANCELLED. The index on `(customer_id, created_at)` is for a later daily-limit lookup by customer and time. That lookup is not implemented yet.

## Q2. Idempotency

A retry is identified by the `Idempotency-Key` header and `request_hash`. The hash is SHA-256 of `customerId|sendCurrency|parsed sendAmount|receiveCurrency|recipientName`. The text `"500000"` and the number `500000` become the same integer, so they share one hash.

The service checks the header, validates the body, then inserts. It does not select first. `TransferInserter` inserts in its own transaction. If that customer already has the key, `uk_transfers_customer_idempotency` rejects the insert and that transaction rolls back. The service then reads the saved row outside that transaction. The same hash returns the stored transfer with 201. A different hash returns 409.

If two identical requests arrive together, both try to insert. The unique constraint lets only one commit. The loser rolls back, reads the winner, sees the same hash, and returns the same `transferId`. The database constraint enforces one row, not a lock in the app.

## Q3. Money

All money in code is `BigDecimal`. There is no `double`. The fee rate `0.01` and the exchange rate `0.0412` are built from strings.

The fee is `sendAmount * 0.01`, rounded once to 0 decimal places with `HALF_UP`, then raised to 3000 if it is smaller. `totalDebit` is send amount plus fee, with no extra rounding. `receiveAmount` is `sendAmount * 0.0412`, rounded once to 2 decimal places with `HALF_UP`. Rounding happens only at the end of each calculation in `TransferCalculator`. A later read or retry returns the stored numbers with `toPlainString()`. It does not calculate again, so `20600.00` keeps the trailing zero.

The API sends amounts as JSON strings. Only `sendAmount` may arrive as a JSON string or a JSON integer. A JSON decimal such as `1.5` is rejected. In the database, KRW send amount, fee, and total debit are `BIGINT`. The PHP receive amount is `DECIMAL(19,2)`.
