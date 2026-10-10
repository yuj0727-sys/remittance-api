`Idempotency-Key` 하나는 사용자 행동 한 번을 뜻합니다. 재시도는 같은 키를 다시 보냅니다.
S1, S2, S3는 11111111-1111-4111-8111-111111111111을 씁니다. S2는 S1을 반복합니다. S3는 키를 유지하고 금액만 바꿉니다.
S4는 22222222-2222-4222-8222-222222222222를 씁니다. S5는 그 송금을 취소합니다. S6는 33333333-3333-4333-8333-333333333333을 씁니다.

### S1
요청:
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
상태: 201
응답:
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
요청:
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
상태: 201
응답:
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
요청:
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
상태: 409
응답:
```json
{
  "error": "CONFLICT",
  "message": "Idempotency-Key was already used with a different request"
}
```

### S4a
요청:
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
상태: 201
응답:
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
요청:
```
POST /api/transfers/0045564d-27f7-422a-9292-b02b3fc20202/cancel
```
상태: 200
응답:
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
요청:
```
POST /api/transfers/0045564d-27f7-422a-9292-b02b3fc20202/cancel
```
상태: 409
응답:
```json
{
  "error": "CONFLICT",
  "message": "cannot cancel transfer in status CANCELLED"
}
```

### S6
요청:
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
상태: 400
응답:
```json
{
  "error": "VALIDATION_ERROR",
  "message": "sendAmount must be at least 10000"
}
```
