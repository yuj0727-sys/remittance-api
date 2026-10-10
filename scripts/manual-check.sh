#!/usr/bin/env bash
set -u
BASE=http://localhost:8080

call() {
  local name="$1" method="$2" path="$3"
  shift 3
  local body_file status
  body_file=$(mktemp)
  status=$(curl -sS -o "$body_file" -w "%{http_code}" -X "$method" "$BASE$path" "$@")
  echo
  echo "===== $name ====="
  echo "STATUS $status"
  python3 -m json.tool "$body_file" 2>/dev/null || cat "$body_file"
  LAST_BODY=$body_file
  LAST_STATUS=$status
}

field() {
  python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$1" "$2"
}

expect() {
  local label="$1" actual="$2" wanted="$3"
  if [ "$actual" = "$wanted" ]; then
    echo "PASS $label = $wanted"
  else
    echo "FAIL $label: got [$actual], wanted [$wanted]"
  fi
}

echo "S1 create"
call S1 POST /api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 11111111-1111-4111-8111-111111111111' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
S1_ID=$(field "$LAST_BODY" transferId)
expect S1.status "$LAST_STATUS" 201
expect S1.transferStatus "$(field "$LAST_BODY" status)" REQUESTED
expect S1.fee "$(field "$LAST_BODY" fee)" 5000
expect S1.totalDebit "$(field "$LAST_BODY" totalDebit)" 505000
expect S1.receiveAmount "$(field "$LAST_BODY" receiveAmount)" 20600.00

echo "S2 same key, same body"
call S2 POST /api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 11111111-1111-4111-8111-111111111111' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
expect S2.status "$LAST_STATUS" 201
expect S2.sameId "$(field "$LAST_BODY" transferId)" "$S1_ID"

echo "S3 same key, amount 600000"
call S3 POST /api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 11111111-1111-4111-8111-111111111111' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"600000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
expect S3.status "$LAST_STATUS" 409
expect S3.error "$(field "$LAST_BODY" error)" CONFLICT

echo "S4 create then cancel"
call S4a POST /api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 22222222-2222-4222-8222-222222222222' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"700000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
S4_ID=$(field "$LAST_BODY" transferId)
expect S4a.status "$LAST_STATUS" 201
call S4b POST "/api/transfers/$S4_ID/cancel"
expect S4b.status "$LAST_STATUS" 200
expect S4b.transferStatus "$(field "$LAST_BODY" status)" CANCELLED

echo "S5 cancel again"
call S5 POST "/api/transfers/$S4_ID/cancel"
expect S5.status "$LAST_STATUS" 409
expect S5.error "$(field "$LAST_BODY" error)" CONFLICT

echo "S6 amount 5000"
call S6 POST /api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 33333333-3333-4333-8333-333333333333' \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"5000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
expect S6.status "$LAST_STATUS" 400
expect S6.error "$(field "$LAST_BODY" error)" VALIDATION_ERROR

echo "S7 C002 daily limit: 500000 x 6 = 3000000, 7th is rejected"
for n in 1 2 3 4 5 6; do
  call "S7-$n" POST /api/transfers \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: 44444444-4444-4444-8444-44444444444$n" \
    -d '{"customerId":"C002","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
  expect "S7-$n.status" "$LAST_STATUS" 201
done
call S7-reject POST /api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 44444444-4444-4444-8444-444444444447' \
  -d '{"customerId":"C002","sendCurrency":"KRW","sendAmount":"10000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
expect S7-reject.status "$LAST_STATUS" 422
expect S7-reject.error "$(field "$LAST_BODY" error)" LIMIT_EXCEEDED

echo "S8 success partnerRef S8OK, then the same callback twice"
call S8-create POST /api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 55555555-5555-4555-8555-555555555555' \
  -d '{"customerId":"C003","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"S8OK"}'
S8_ID=$(field "$LAST_BODY" transferId)
call S8-send POST "/api/transfers/$S8_ID/send"
expect S8-send.status "$LAST_STATUS" 200
expect S8-send.transferStatus "$(field "$LAST_BODY" status)" SENDING
call S8-callback-1 POST /api/callbacks/partner \
  -H 'Content-Type: application/json' \
  -d '{"partnerRef":"S8OK","eventId":"evt_0001","status":"COMPLETED"}'
expect S8-callback-1.status "$LAST_STATUS" 200
call S8-callback-2 POST /api/callbacks/partner \
  -H 'Content-Type: application/json' \
  -d '{"partnerRef":"S8OK","eventId":"evt_0001","status":"COMPLETED"}'
expect S8-callback-2.status "$LAST_STATUS" 200
call S8-get GET "/api/transfers/$S8_ID"
expect S8-final "$(field "$LAST_BODY" status)" COMPLETED

echo "S9 failing partnerRef S9FAIL. Watch the server log for 3 partner attempt lines."
call S9-create POST /api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 66666666-6666-4666-8666-666666666666' \
  -d '{"customerId":"C003","sendCurrency":"KRW","sendAmount":"20000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz","partnerRef":"S9FAIL"}'
S9_ID=$(field "$LAST_BODY" transferId)
call S9-send POST "/api/transfers/$S9_ID/send"
expect S9-send.transferStatus "$(field "$LAST_BODY" status)" FAILED
expect S9-reason "$(field "$LAST_BODY" failureReason)" "partner failed 3 times"