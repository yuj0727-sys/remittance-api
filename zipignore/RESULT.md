`Idempotency-Key` 하나는 사용자 행동 한 번을 뜻합니다. 재시도는 같은 키를 다시 보냅니다.
S1, S2, S3는 11111111-1111-4111-8111-111111111111을 씁니다. S2는 S1을 반복합니다. S3는 키를 유지하고 금액만 바꿉니다.
S4는 22222222-2222-4222-8222-222222222222를 씁니다. S5는 그 송금을 취소합니다. S6는 33333333-3333-4333-8333-333333333333을 씁니다.
S7은 C002입니다. 키 44444444-4444-4444-8444-444444444441부터 44444444-4444-4444-8444-444444444445까지는 500000을 보내고 201입니다. 여섯 번째 키 44444444-4444-4444-8444-444444444446이 마지막 성공입니다. 키 44444444-4444-4444-8444-444444444447은 10000을 보내고 거절됩니다.
S8은 55555555-5555-4555-8555-555555555555와 partnerRef S8OK를 씁니다. 같은 콜백 eventId evt_0001을 두 번 보냅니다.
S9는 66666666-6666-4666-8666-666666666666과 partnerRef S9FAIL을 씁니다. 파트너 호출 3번이 모두 실패합니다.

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
요청:
```
POST /api/transfers/97654764-43db-4527-b131-1d6cacb1365c/cancel
```
상태: 200
응답:
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
요청:
```
POST /api/transfers/97654764-43db-4527-b131-1d6cacb1365c/cancel
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

### S7 마지막 성공
C002의 앞선 다섯 건도 201이었습니다. 아래는 여섯 번째이고, 서울 날짜 합계는 정확히 3000000입니다.
요청:
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
상태: 201
응답:
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

### S7 거절
요청:
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
상태: 422
응답:
```json
{
  "error": "LIMIT_EXCEEDED",
  "message": "daily limit of 3000000 KRW exceeded"
}
```

### S8a
요청:
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
상태: 201
응답:
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
요청:
```
POST /api/transfers/188bf1f6-c560-457c-b93f-5f205f1edee3/send
```
상태: 200
응답:
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
요청:
```
POST /api/callbacks/partner
Content-Type: application/json

{
  "partnerRef": "S8OK",
  "eventId": "evt_0001",
  "status": "COMPLETED"
}
```
상태: 200
응답:
```json
{
  "transferId": "188bf1f6-c560-457c-b93f-5f205f1edee3",
  "status": "COMPLETED"
}
```

### S8d
같은 eventId를 다시 보냅니다.
요청:
```
POST /api/callbacks/partner
Content-Type: application/json

{
  "partnerRef": "S8OK",
  "eventId": "evt_0001",
  "status": "COMPLETED"
}
```
상태: 200
응답:
```json
{
  "transferId": "188bf1f6-c560-457c-b93f-5f205f1edee3",
  "status": "COMPLETED"
}
```

### S8 최종 상태
요청:
```
GET /api/transfers/188bf1f6-c560-457c-b93f-5f205f1edee3
```
상태: 200
응답:
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
요청:
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
상태: 201
응답:
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

### S9 재시도 로그
```
2026-10-10T17:58:03.920+09:00  INFO 11451 --- [nio-8081-exec-1] com.dbwjd.transfer.TransferSendService   : partner attempt transferId=498ed94a-2ea1-4e96-b934-92988a8a4e6b attempt=1/3 partnerRef=S9FAIL result=FAILED
2026-10-10T17:58:04.327+09:00  INFO 11451 --- [nio-8081-exec-1] com.dbwjd.transfer.TransferSendService   : partner attempt transferId=498ed94a-2ea1-4e96-b934-92988a8a4e6b attempt=2/3 partnerRef=S9FAIL result=FAILED
2026-10-10T17:58:04.938+09:00  INFO 11451 --- [nio-8081-exec-1] com.dbwjd.transfer.TransferSendService   : partner attempt transferId=498ed94a-2ea1-4e96-b934-92988a8a4e6b attempt=3/3 partnerRef=S9FAIL result=FAILED
```

### S9 최종 상태
요청:
```
POST /api/transfers/498ed94a-2ea1-4e96-b934-92988a8a4e6b/send
```
상태: 200
응답:
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
