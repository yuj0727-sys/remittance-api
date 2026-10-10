Each Idempotency-Key stands for one user action. A retry sends the same key again.
S1, S2, and S3 use 11111111-1111-4111-8111-111111111111. S2 repeats S1. S3 keeps the key and changes the amount.
S4 uses 22222222-2222-4222-8222-222222222222. S5 cancels that same transfer. S6 uses 33333333-3333-4333-8333-333333333333.
S7 uses C002. Keys 44444444-4444-4444-8444-444444444441 through 44444444-4444-4444-8444-444444444445 send 500000 and return 201. The sixth key, 44444444-4444-4444-8444-444444444446, is the last success. Key 44444444-4444-4444-8444-444444444447 sends 10000 and is rejected.
S8 uses 55555555-5555-4555-8555-555555555555 and partnerRef S8OK. The same callback eventId evt_0001 is sent twice.
S9 uses 66666666-6666-4666-8666-666666666666 and partnerRef S9FAIL. All 3 partner tries fail.

### S1
Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 11111111-1111-4111-8111-111111111111

{
  "customerId": "C001",
  "sendCurrency": "KRW",
  "sendAmount": "500000",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
}
```
Status: 201
Response:
```json
{
  "transferId": "e9461454-4186-4a88-9ba1-a276ce0bcffb",
  "status": "REQUESTED",
  "sendAmount": "500000",
  "fee": "5000",
  "totalDebit": "505000",
  "receiveAmount": "20600.00",
  "createdAt": "2026-10-10T08:58:03.416547Z",
  "customerId": "C001",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "PR-f8b818dc-ee2d-4384-9376-984fdb423d35",
  "failureReason": null
}
```

### S2
Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 11111111-1111-4111-8111-111111111111

{
  "customerId": "C001",
  "sendCurrency": "KRW",
  "sendAmount": "500000",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
}
```
Status: 201
Response:
```json
{
  "transferId": "e9461454-4186-4a88-9ba1-a276ce0bcffb",
  "status": "REQUESTED",
  "sendAmount": "500000",
  "fee": "5000",
  "totalDebit": "505000",
  "receiveAmount": "20600.00",
  "createdAt": "2026-10-10T08:58:03.416547Z",
  "customerId": "C001",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "PR-f8b818dc-ee2d-4384-9376-984fdb423d35",
  "failureReason": null
}
```

### S3
Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 11111111-1111-4111-8111-111111111111

{
  "customerId": "C001",
  "sendCurrency": "KRW",
  "sendAmount": "600000",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
}
```
Status: 409
Response:
```json
{
  "error": "CONFLICT",
  "message": "Idempotency-Key was already used with a different request"
}
```

### S4a
Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 22222222-2222-4222-8222-222222222222

{
  "customerId": "C001",
  "sendCurrency": "KRW",
  "sendAmount": "700000",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
}
```
Status: 201
Response:
```json
{
  "transferId": "97654764-43db-4527-b131-1d6cacb1365c",
  "status": "REQUESTED",
  "sendAmount": "700000",
  "fee": "7000",
  "totalDebit": "707000",
  "receiveAmount": "28840.00",
  "createdAt": "2026-10-10T08:58:03.444762Z",
  "customerId": "C001",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "PR-65cc98b5-1a2b-41ec-8559-7412e1a97e7a",
  "failureReason": null
}
```

### S4b
Request:
```
POST /api/transfers/97654764-43db-4527-b131-1d6cacb1365c/cancel
```
Status: 200
Response:
```json
{
  "transferId": "97654764-43db-4527-b131-1d6cacb1365c",
  "status": "CANCELLED",
  "sendAmount": "700000",
  "fee": "7000",
  "totalDebit": "707000",
  "receiveAmount": "28840.00",
  "createdAt": "2026-10-10T08:58:03.444762Z",
  "customerId": "C001",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "PR-65cc98b5-1a2b-41ec-8559-7412e1a97e7a",
  "failureReason": null
}
```

### S5
Request:
```
POST /api/transfers/97654764-43db-4527-b131-1d6cacb1365c/cancel
```
Status: 409
Response:
```json
{
  "error": "CONFLICT",
  "message": "cannot cancel transfer in status CANCELLED"
}
```

### S6
Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 33333333-3333-4333-8333-333333333333

{
  "customerId": "C001",
  "sendCurrency": "KRW",
  "sendAmount": "5000",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
}
```
Status: 400
Response:
```json
{
  "error": "VALIDATION_ERROR",
  "message": "sendAmount must be at least 10000"
}
```

### S7 last success
The first five creates for C002 also returned 201. This is the sixth, and the Seoul-day sum is exactly 3000000.
Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 44444444-4444-4444-8444-444444444446

{
  "customerId": "C002",
  "sendCurrency": "KRW",
  "sendAmount": "500000",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
}
```
Status: 201
Response:
```json
{
  "transferId": "0162b1ab-e4ad-4e5b-901b-29be77b17c07",
  "status": "REQUESTED",
  "sendAmount": "500000",
  "fee": "5000",
  "totalDebit": "505000",
  "receiveAmount": "20600.00",
  "createdAt": "2026-10-10T08:58:03.474865Z",
  "customerId": "C002",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "PR-dff4aa20-0698-4193-aab2-43713b348f79",
  "failureReason": null
}
```

### S7 rejected
Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 44444444-4444-4444-8444-444444444447

{
  "customerId": "C002",
  "sendCurrency": "KRW",
  "sendAmount": "10000",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
}
```
Status: 422
Response:
```json
{
  "error": "LIMIT_EXCEEDED",
  "message": "daily limit of 3000000 KRW exceeded"
}
```

### S8a
Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 55555555-5555-4555-8555-555555555555

{
  "customerId": "C003",
  "sendCurrency": "KRW",
  "sendAmount": "500000",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "S8OK"
}
```
Status: 201
Response:
```json
{
  "transferId": "188bf1f6-c560-457c-b93f-5f205f1edee3",
  "status": "REQUESTED",
  "sendAmount": "500000",
  "fee": "5000",
  "totalDebit": "505000",
  "receiveAmount": "20600.00",
  "createdAt": "2026-10-10T08:58:03.480063Z",
  "customerId": "C003",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "S8OK",
  "failureReason": null
}
```

### S8b
Request:
```
POST /api/transfers/188bf1f6-c560-457c-b93f-5f205f1edee3/send
```
Status: 200
Response:
```json
{
  "transferId": "188bf1f6-c560-457c-b93f-5f205f1edee3",
  "status": "SENDING",
  "sendAmount": "500000",
  "fee": "5000",
  "totalDebit": "505000",
  "receiveAmount": "20600.00",
  "createdAt": "2026-10-10T08:58:03.480063Z",
  "customerId": "C003",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "S8OK",
  "failureReason": null
}
```

### S8c
Request:
```
POST /api/callbacks/partner
Content-Type: application/json

{
  "partnerRef": "S8OK",
  "eventId": "evt_0001",
  "status": "COMPLETED"
}
```
Status: 200
Response:
```json
{
  "transferId": "188bf1f6-c560-457c-b93f-5f205f1edee3",
  "status": "COMPLETED"
}
```

### S8d
The same eventId is sent again.
Request:
```
POST /api/callbacks/partner
Content-Type: application/json

{
  "partnerRef": "S8OK",
  "eventId": "evt_0001",
  "status": "COMPLETED"
}
```
Status: 200
Response:
```json
{
  "transferId": "188bf1f6-c560-457c-b93f-5f205f1edee3",
  "status": "COMPLETED"
}
```

### S8 final status
Request:
```
GET /api/transfers/188bf1f6-c560-457c-b93f-5f205f1edee3
```
Status: 200
Response:
```json
{
  "transferId": "188bf1f6-c560-457c-b93f-5f205f1edee3",
  "status": "COMPLETED",
  "sendAmount": "500000",
  "fee": "5000",
  "totalDebit": "505000",
  "receiveAmount": "20600.00",
  "createdAt": "2026-10-10T08:58:03.480063Z",
  "customerId": "C003",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "S8OK",
  "failureReason": null
}
```

### S9a
Request:
```
POST /api/transfers
Content-Type: application/json
Idempotency-Key: 66666666-6666-4666-8666-666666666666

{
  "customerId": "C003",
  "sendCurrency": "KRW",
  "sendAmount": "20000",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "S9FAIL"
}
```
Status: 201
Response:
```json
{
  "transferId": "498ed94a-2ea1-4e96-b934-92988a8a4e6b",
  "status": "REQUESTED",
  "sendAmount": "20000",
  "fee": "3000",
  "totalDebit": "23000",
  "receiveAmount": "824.00",
  "createdAt": "2026-10-10T08:58:03.713984Z",
  "customerId": "C003",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "S9FAIL",
  "failureReason": null
}
```

### S9 retry log
```
2026-10-10T17:58:03.920+09:00  INFO 11451 --- [nio-8081-exec-1] com.dbwjd.transfer.TransferSendService   : partner attempt transferId=498ed94a-2ea1-4e96-b934-92988a8a4e6b attempt=1/3 partnerRef=S9FAIL result=FAILED
2026-10-10T17:58:04.327+09:00  INFO 11451 --- [nio-8081-exec-1] com.dbwjd.transfer.TransferSendService   : partner attempt transferId=498ed94a-2ea1-4e96-b934-92988a8a4e6b attempt=2/3 partnerRef=S9FAIL result=FAILED
2026-10-10T17:58:04.938+09:00  INFO 11451 --- [nio-8081-exec-1] com.dbwjd.transfer.TransferSendService   : partner attempt transferId=498ed94a-2ea1-4e96-b934-92988a8a4e6b attempt=3/3 partnerRef=S9FAIL result=FAILED
```

### S9 final status
Request:
```
POST /api/transfers/498ed94a-2ea1-4e96-b934-92988a8a4e6b/send
```
Status: 200
Response:
```json
{
  "transferId": "498ed94a-2ea1-4e96-b934-92988a8a4e6b",
  "status": "FAILED",
  "sendAmount": "20000",
  "fee": "3000",
  "totalDebit": "23000",
  "receiveAmount": "824.00",
  "createdAt": "2026-10-10T08:58:03.713984Z",
  "customerId": "C003",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan",
  "partnerRef": "S9FAIL",
  "failureReason": "partner failed 3 times"
}
```
