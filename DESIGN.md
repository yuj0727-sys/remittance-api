# Design

## Q1. Tables

`customers` has only `customer_id` and `name`. Seed rows are C001 Kim, C002 Lee, and C003 Park.

`transfers` is one remittance. The primary key on `transfer_id` stops two rows from sharing an id. The foreign key on `customer_id` stops a transfer for a customer that does not exist. `UNIQUE (customer_id, idempotency_key)` stops one customer from storing the same key twice, so only one of two concurrent inserts can commit. `partner_ref` is required and unique, so two transfers cannot share a partner reference. `failure_reason` is optional text. It stays null until three partner calls fail, and then it stores `partner failed 3 times`. Checks stop a send amount below 10000, a fee below 3000, a total debit that is not send amount plus fee, and a status outside REQUESTED, SENDING, COMPLETED, FAILED, and CANCELLED. The index on `(customer_id, created_at)` supports the daily-limit sum for one customer and one time range.

`callback_events` stores one partner callback. The primary key on `event_id` stops the same event from being stored twice. Each row also has `partner_ref` and `received_at`. There is no foreign key to `transfers`.

## Q2. Idempotency

A retry is identified by the `Idempotency-Key` header and `request_hash`. The hash is SHA-256 of `customerId|sendCurrency|parsed sendAmount|receiveCurrency|recipientName|partnerRef`. The text `"500000"` and the number `500000` become the same integer, so they share one hash. The `partnerRef` in the hash is only the client value. If the client omits it, that part is empty. The generated `PR-` id is not hashed.

The service validates the body, checks the header, then calls `TransferInserter`. Under the customer lock, the inserter reads the key first. The same hash returns the saved transfer with 201 and skips the daily limit. A different hash returns 409. A new key is inserted in that same transaction. If `uk_transfers_customer_idempotency` or `uk_transfers_partner_ref` rejects the insert, that transaction rolls back. The service then reads outside it. The same hash returns the stored transfer with 201. A different hash returns 409. If no row has that key, the lost unique key was `partner_ref`, and the response is 409 `partnerRef already used`.

If two identical requests for one customer arrive together, the customer lock runs them one by one. The second sees the saved row and returns the same `transferId`. Two customers can still race on the same `partner_ref`. That unique key lets only one commit.

## Q3. Money

All money in code is `BigDecimal`. There is no `double`. The fee rate `0.01` and the exchange rate `0.0412` are built from strings.

The fee is `sendAmount * 0.01`, rounded once to 0 decimal places with `HALF_UP`, then raised to 3000 if it is smaller. `totalDebit` is send amount plus fee, with no extra rounding. `receiveAmount` is `sendAmount * 0.0412`, rounded once to 2 decimal places with `HALF_UP`. Rounding happens only at the end of each calculation in `TransferCalculator`. A later read or retry returns the stored numbers with `toPlainString()`. It does not calculate again, so `20600.00` keeps the trailing zero.

The API sends amounts as JSON strings. Only `sendAmount` may arrive as a JSON string or a JSON integer. A JSON decimal such as `1.5` is rejected. In the database, KRW send amount, fee, and total debit are `BIGINT`. The PHP receive amount is `DECIMAL(19,2)`. `partner_ref`, `failure_reason`, and `callback_events` store no money.

## Q4. Daily limit lock

The mechanism is a pessimistic lock on the customer row: `SELECT ... FOR UPDATE` (`LockModeType.PESSIMISTIC_WRITE`). It is held until the create transaction commits.

After the lock the order is: read the idempotency key, check the daily limit, then insert. The same key and hash return the saved row and skip the limit. A different hash returns 409. The limit sums that customer's `sendAmount` for the Seoul day, excluding `CANCELLED` and `FAILED`. Above 3,000,000 returns 422. Exactly 3,000,000 is allowed.

We gave up parallel creates for one customer, so that customer's throughput is lower. Other customers are not blocked.

Without the lock, two different keys can both read the old sum, both pass, and both insert. The customer can go over 3,000,000. The unique key still stops one key from inserting twice. It does not protect the sum.

## Q5. Callback races

P6 ran three races against the current code. None failed.

In A, the fake partner calls the callback just before returning `ACCEPTED`. Send has already committed `SENDING` and does not write status after `ACCEPTED`. The callback sets `COMPLETED`. That stays the final status.

In B, the fake partner returns `FAILED` three times and calls the callback before the last try. The callback sets `COMPLETED`. The failure save sees that `COMPLETED` cannot move to `FAILED`, logs `partner failure not saved`, and returns before the update. The status stays `COMPLETED`. This is the status check, not a zero-row update. No code change is needed.

In C, a callback while the transfer is still `REQUESTED` returns 409 `cannot complete transfer in status REQUESTED`. The event row rolls back. This is a limit: the partner must retry after the transfer is `SENDING`. We do not store an early callback to apply later.
