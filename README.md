# Remittance API from Korea to the Philippines

## Run

Java 17 is required. You do not need to install Maven. `./mvnw` downloads Maven 3.9.11 on the first run.

```shell
./mvnw spring-boot:run
```

The server listens on `http://localhost:8080`. Data stays in memory and is gone when the process stops.

## Stack

Java 17, Spring Boot 3, Spring Data JPA, and H2 in memory.

## Retry a create safely

The header name is `Idempotency-Key`. The client creates the value. Make one UUID v4 for each user action, and send that same value again when you retry the action.

A missing, blank, or longer-than-64 header is 400. The same key and the same body do not create a new transfer. They return the original transfer with 201. The same key with a different body is 409.

```shell
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 11111111-1111-4111-8111-111111111111' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan"}'
```

## Optional partnerRef

`partnerRef` on create is optional. If you send it, it must be 1 to 64 letters, digits, underscores, or hyphens. Anything else is 400. If you omit it, the server stores `PR-` plus a UUID. The value you send is part of the idempotency hash. The generated value is not.

`partner_ref` is unique. The same key and the same body return the original transfer. Another transfer that reuses the same `partnerRef` gets 409 `partnerRef already used`.

## Send

Create does not call the partner. `POST /api/transfers/{transferId}/send` starts it. The row moves from `REQUESTED` to `SENDING`, then the partner is called up to 3 times. `ACCEPTED` means the partner got the request. The status stays `SENDING` until a callback sets `COMPLETED`. Three failures set `FAILED` and `failureReason` to `partner failed 3 times`. `S9FAIL` fails. `S8OK` and `OK_REF` succeed.

```shell
curl -i -X POST http://localhost:8080/api/transfers/TRANSFER_ID/send
```

## Partner callback

`POST /api/callbacks/partner` needs `partnerRef`, `eventId`, and `status`. `status` must be `COMPLETED`. The same `eventId` for the same `partnerRef` returns 200 and does not change the transfer.

```shell
curl -i -X POST http://localhost:8080/api/callbacks/partner \
  -H 'Content-Type: application/json' \
  -d '{"partnerRef":"OK_REF","eventId":"event-1","status":"COMPLETED"}'
```

## AI tools

- **Claude:** Wrote prompts for the implementation and test work
- **Cursor:** Implemented the features and test code from those prompts
- **ChatGPT:** Checked technical ideas and how the code works when something was unclear

## Assumptions

- `Idempotency-Key` is required.
- A key is scoped by `customerId`. Another customer may use the same key.
- A retry of the same request returns 201 and the original body.
- `sendAmount` may be a JSON string or a JSON integer. A JSON decimal such as `1.5` is rejected.
- Body validation runs before the idempotency lookup. A bad body does not look up an existing key.
- Sending starts at `POST /api/transfers/{id}/send`. Create does not call the partner. An automatic call on create would leave the row past `REQUESTED`, and cancel scenario S4 would fail.
- The daily limit sums `sendAmount` only. The fee is not included. A Seoul-day sum of exactly 3000000 KRW is allowed. `CANCELLED` and `FAILED` rows are not counted.
- The same callback `eventId` for the same `partnerRef` returns 200. The transfer is not updated again.
- Partner success or failure is fixed by `partnerRef`: `abs(hashCode) % 10 < 3` fails. Three tries cannot change that result. `S9FAIL` fails. `S8OK` and `OK_REF` succeed.
- Each partner try is written only to the log (`partner attempt ...`). There is no attempt table. `failure_reason` stores text only when the transfer becomes `FAILED`.

## Not implemented

There is no authentication, no transfer list, and no pagination. The partner client does not call a remote server. A callback that arrives while the transfer is still `REQUESTED` returns 409, and that event is not stored. The partner must retry the same callback after the transfer is `SENDING`.
