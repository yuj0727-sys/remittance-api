Each Idempotency-Key stands for one user action. A retry sends the same key again.
S1, S2, and S3 use 11111111-1111-4111-8111-111111111111. S2 repeats S1. S3 keeps the key and changes the amount.
S4 uses 22222222-2222-4222-8222-222222222222. S5 cancels that same transfer. S6 uses 33333333-3333-4333-8333-333333333333.

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
  "transferId": "0531fcae-bd79-41bb-b489-02e5456a3d6c",
  "status": "REQUESTED",
  "sendAmount": "500000",
  "fee": "5000",
  "totalDebit": "505000",
  "receiveAmount": "20600.00",
  "createdAt": "2026-10-09T09:54:47.624764Z",
  "customerId": "C001",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
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
  "transferId": "0531fcae-bd79-41bb-b489-02e5456a3d6c",
  "status": "REQUESTED",
  "sendAmount": "500000",
  "fee": "5000",
  "totalDebit": "505000",
  "receiveAmount": "20600.00",
  "createdAt": "2026-10-09T09:54:47.624764Z",
  "customerId": "C001",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
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
  "transferId": "0045564d-27f7-422a-9292-b02b3fc20202",
  "status": "REQUESTED",
  "sendAmount": "700000",
  "fee": "7000",
  "totalDebit": "707000",
  "receiveAmount": "28840.00",
  "createdAt": "2026-10-09T09:54:47.712233Z",
  "customerId": "C001",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
}
```

### S4b
Request:
```
POST /api/transfers/0045564d-27f7-422a-9292-b02b3fc20202/cancel
```
Status: 200
Response:
```json
{
  "transferId": "0045564d-27f7-422a-9292-b02b3fc20202",
  "status": "CANCELLED",
  "sendAmount": "700000",
  "fee": "7000",
  "totalDebit": "707000",
  "receiveAmount": "28840.00",
  "createdAt": "2026-10-09T09:54:47.712233Z",
  "customerId": "C001",
  "sendCurrency": "KRW",
  "receiveCurrency": "PHP",
  "recipientName": "Juan"
}
```

### S5
Request:
```
POST /api/transfers/0045564d-27f7-422a-9292-b02b3fc20202/cancel
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

